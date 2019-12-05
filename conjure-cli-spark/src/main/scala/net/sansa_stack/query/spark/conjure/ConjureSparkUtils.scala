package net.sansa_stack.query.spark.conjure

import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters.asScalaBufferConverter

import org.aksw.conjure.cli.config.ConjureConfig
import org.aksw.conjure.cli.config.ConjureProcessor
import org.aksw.conjure.cli.main.CommandMain
import org.aksw.conjure.cli.main.MainCliConjureNative
import org.aksw.dcat.ap.utils.DcatUtils
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl
import org.apache.jena.ext.com.google.common.base.StandardSystemProperty
import org.apache.jena.ext.com.google.common.base.Stopwatch
import org.apache.jena.ext.com.google.common.collect.ImmutableRangeSet
import org.apache.jena.ext.com.google.common.collect.Range
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.sys.JenaSystem
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

import com.google.common.hash.Hashing
import com.typesafe.scalalogging.LazyLogging

object ConjureSparkUtils extends LazyLogging {

  def createPartitionKey(dcatDataset: Resource): String = {
    val key = Option(DcatUtils.getFirstDownloadUrl(dcatDataset)).getOrElse("")

    return key
  }

  def mainSpark(cm: CommandMain, catalogDataRef: DataRef, job: Job, conjureConfig: ConjureConfig): Unit = { /* args: Array[String]): Unit = { */

    // val sourcePathToContent: java.util.Map[Path, Array[Byte]] = conjureConfig.getSourcePathToContent

    logger.info("Setting up spark for conjure job execution")

    // val catalogUrl = if (args.length == 0) "http://localhost/~raven/conjure.test.dcat.ttl" else args(0)
    val args = Array[String]()
    val limit = if (args.length > 1) args(1).toInt else 10
    val numThreads = if (args.length > 2) args(2).toInt else 4

    // Fix for an issue with non-existing directory
    val tmpDirStr = StandardSystemProperty.JAVA_IO_TMPDIR.value()
    if (tmpDirStr == null) {
      throw new RuntimeException("Could not obtain temporary directory")
    }
    val sparkEventsDir = new File(tmpDirStr + "/spark-events")
    if (!sparkEventsDir.exists()) {
      sparkEventsDir.mkdirs()
    }

    // Lambda that maps host names to allowed port ranges (for the triple store)
    // Only ImmutableRangeSet provides the .asSet(discreteDomain) view
    // TODO Move to config object
    val hostToPortRanges: String => ImmutableRangeSet[Integer] =
      hostName => new ImmutableRangeSet.Builder[Integer].add(Range.closed(3030, 3040)).build()

    // File.createTempFile("spark-events")
    val numPartitions = numThreads * 1

    val masterHostname = InetAddress.getLocalHost.getHostName

    val builder = SparkSession.builder

    if (!masterHostname.toLowerCase.contains("qrowd")) {
      builder.master(s"local[$numThreads]")
    }

    val sparkSession = builder
      .appName("Sansa-Conjure Test")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.eventLog.enabled", "true")
      .config("spark.kryo.registrator", String.join(
        ", ",
        "net.sansa_stack.query.spark.conjure.kryo.KryoRegistratorRDFNode"))
      .config("spark.default.parallelism", s"$numThreads")
      .config("spark.sql.shuffle.partitions", s"$numThreads")
      .getOrCreate()

    sparkSession.conf.set("spark.sql.crossJoin.enabled", "true")

    // val sourcePathToContent: java.util.Map[Path, Array[Byte]] = null

    val configBroadcast: Broadcast[ConjureConfig] =
      sparkSession.sparkContext.broadcast(conjureConfig)

    val hostToPortRangesBroadcast: Broadcast[String => ImmutableRangeSet[Integer]] =
      sparkSession.sparkContext.broadcast(hostToPortRanges)

    // TODO Circular init issue with DefaultPrefixes
    // We could use ARQConstants.getGlobalPrefixMap()
    JenaSystem.init

    // TODO Maybe accumulate on the workers if distinct doesn't do that already
    // Probe for the hostnames of available workers
    val maxWorkerNodeProbeRddSize = 1000
    val probeRdd = sparkSession.sparkContext.parallelize(Seq.range(0, maxWorkerNodeProbeRddSize))

    val workerNodeHostNames = probeRdd
      .mapPartitions(_ => Iterator(InetAddress.getLocalHost.getHostName))
      .distinct.collect.sorted.toList

    logger.info("Hostnames: " + workerNodeHostNames)

    val numWorkerNodes = workerNodeHostNames.size
    // Create hash from the lexicographically lowest downloadURL

    // Use guava's hashing facilities to create a
    // non JVM-dependent hash (in contrast to Object.hashCode)
    val hashFunction = Hashing.goodFastHash(32)
    val hashInt: Resource => Int = res => math.abs(hashFunction.newHasher.putString(
      createPartitionKey(res), StandardCharsets.UTF_8).hash().asInt().toInt)

    /*
    for (item <- dcatRdd.collect) {
      logger.info(item)
      RDFDataMgr.write(System.out, item.getModel, RDFFormat.TURTLE_PRETTY)
    }
    */

    val model = ModelFactory.createDefaultModel
    // Note .asResource yields a Jena ResourceImpl instead of 'this'
    // so that kryo can serialize it
    // The .asResource behavior is subject to change. If it breaks, create an implicit
    // function such as Resource.toDefaultResource
    val jobBroadcast: Broadcast[Resource] = sparkSession.sparkContext.broadcast(job.asResource)

    val repo = HttpResourceRepositoryFromFileSystemImpl.createDefault();
    val initialTaskContexts = MainCliConjureNative.createTasksContexts(catalogDataRef, job, repo).asScala

    // val url = DcatUtils.getFirstDownloadUrl(dcat)
    // r = MainCliConjureSimple.executeJob(taskContexts, job, repo, cacheStore);

    // Combine the data with the location preferences
    val taskContextsWithPreferredLocation =
      initialTaskContexts
        .map(tc => (tc, Seq(workerNodeHostNames(hashInt(tc.getInputRecord) % numWorkerNodes))))

    for (item <- taskContextsWithPreferredLocation) {
      logger.info("Item: " + item)
      //      RDFDataMgr.write(System.out, item.getModel, RDFFormat.TURTLE_PRETTY)
    }

    // The RDD does not contain the location preferences anymore of course
    val dcatRdd = sparkSession.sparkContext
      .makeRDD(taskContextsWithPreferredLocation)
      .coalesce(numPartitions)

    logger.info("NUM PARTITIONS = " + dcatRdd.getNumPartitions)

    val resultCatalogRdd = dcatRdd.mapPartitions(taskContextIt => {
      val hostname = InetAddress.getLocalHost.getHostName

      // Get the config files from the broadcast variable and write them to temporary files
      // Then start a spring application from them
      val config = configBroadcast.value

      // TODO Allocate a fresh folder (e.g. with timestamp or counter)
      val tmpPath = Files.createTempDirectory("conjure-config-")

      val sourceToPath = MainCliConjureNative.writeFiles(tmpPath, conjureConfig.getSourceToContent)
      val effectiveSources = ConjureConfig.effectiveSources(config.getSources, sourceToPath)

      logger.info("HOST " + hostname + " Raw file sources: " + sourceToPath.keySet)
      logger.info("HOST " + hostname + " Effective file sources: " + effectiveSources)

      // New set up the spring app for this partition

      //      val properties = ImmutableMap.builder[String, Object]
      //        .put("task", sourcePathToContent)
      //        .build

      val app: SpringApplication = new SpringApplicationBuilder()
        //        .properties(properties)
        .sources(classOf[ConfigConjureSparkProcessor])
        .bannerMode(Banner.Mode.OFF)
        // If true, Desktop.isDesktopSupported() will return false, meaning we can't
        // launch a browser
        .headless(false)
        .web(WebApplicationType.NONE)
        .build()

      app.setSources(effectiveSources)

      val ctx: ConfigurableApplicationContext = app.run() // args: _*)

      val processor: ConjureProcessor = ctx.getBean(classOf[ConjureProcessor])

      taskContextIt.map(x => processor.process(x))
    })

    val stopwatch = Stopwatch.createStarted()
    val evalResult = resultCatalogRdd.collect

    logger.info("RESULTS: ----------------------------")
    for (item <- evalResult) {
      // logger.info("Result status: " + item)
      RDFDataMgr.write(System.out, item.getDcatRecord.getModel, RDFFormat.TURTLE_PRETTY)
    }

    logger.info("Processed " + evalResult.length + " items in " + (stopwatch.stop.elapsed(TimeUnit.MILLISECONDS) * 0.001) + " seconds")

    // Set up a dataset processing expression
    //      logger.info("Conjure spec is:");
    //      RDFDataMgr.write(System.err, effectiveWorkflow.getModel(), RDFFormat.TURTLE_PRETTY);
    /*
      try(RdfDataObject data = effectiveWorkflow.accept(executor)) {
        try(RDFConnection conn = data.openConnection()) {
          // Print out the data that is the process result
          Model model = conn.queryConstruct("CONSTRUCT WHERE { ?s ?p ?o }");

          RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
        }
      } catch(Exception e) {
        logger.warn("Failed to process " + url, e);
      }
    }
*/

    sparkSession.stop
    sparkSession.close()

    logger.info("Done")
  }
}
