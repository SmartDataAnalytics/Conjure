package net.sansa_stack.query.spark.conjure

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._

import org.aksw.conjure.cli.main.CommandMain
import org.aksw.conjure.cli.main.ConfigCliConjure
import org.aksw.conjure.cli.main.ConfigGroovy
import org.aksw.conjure.cli.main.MainCliConjureSimple
import org.aksw.dcat.ap.utils.DcatUtils
import org.aksw.jena_sparql_api.common.DefaultPrefixes
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef
import org.aksw.jena_sparql_api.conjure.dataset.engine.{ TaskContext => ConjureTaskContext }
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl
import org.apache.jena.ext.com.google.common.base.StandardSystemProperty
import org.apache.jena.ext.com.google.common.base.Stopwatch
import org.apache.jena.ext.com.google.common.collect.ImmutableRangeSet
import org.apache.jena.ext.com.google.common.collect.Range
import org.apache.jena.query.Syntax
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.sys.JenaSystem
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql._
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

import com.beust.jcommander.JCommander
import com.google.common.hash.Hashing
import com.typesafe.scalalogging.LazyLogging
import java.util.Arrays

object MainCliConjureSpark extends LazyLogging {
  def main(args: Array[String]): Unit = {
    val cm = new CommandMain()
    val jc = JCommander.newBuilder()
      .addObject(cm)
      .build()

    jc.parse(args:_*);

    if (cm.help) {
      jc.usage();
      return ;
    }

    if (cm.inputModelFile == null) {
      throw new RuntimeException("No input (catalog) model provided");
    }

    val ctx: ConfigurableApplicationContext = new SpringApplicationBuilder()
      .sources(classOf[ConfigGroovy], classOf[ConfigCliConjure])
      .bannerMode(Banner.Mode.OFF)
      // If true, Desktop.isDesktopSupported() will return false, meaning we can't
      // launch a browser
      .headless(false)
      .web(WebApplicationType.NONE)
      .run(args:_*)
  }

  def createPartitionKey(dcatDataset: Resource): String = {
    val key = Option(DcatUtils.getFirstDownloadUrl(dcatDataset)).getOrElse("")

    return key
  }

  def mainSpark(cm: CommandMain, catalogDataRef: DataRef, job: Job): Unit = { /* args: Array[String]): Unit = { */

    //val catalogUrl = if (args.length == 0) "http://localhost/~raven/conjure.test.dcat.ttl" else args(0)
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

    val masterHostname = InetAddress.getLocalHost.getHostName;

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
        "net.sansa_stack.query.spark.conjure.KryoRegistratorRDFNode"))
      .config("spark.default.parallelism", s"$numThreads")
      .config("spark.sql.shuffle.partitions", s"$numThreads")
      .getOrCreate()

    sparkSession.conf.set("spark.sql.crossJoin.enabled", "true")

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
    val initialTaskContexts = MainCliConjureSimple.createTasksContexts(catalogDataRef, job, repo).asScala
    
    
        //val url = DcatUtils.getFirstDownloadUrl(dcat)
        //    r = MainCliConjureSimple.executeJob(taskContexts, job, repo, cacheStore);

      
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


    
    val executiveRdd = dcatRdd.mapPartitions(taskContextIt => {
      val jobRdfNode = jobBroadcast.value;
      val baos = new ByteArrayOutputStream
      RDFDataMgr.write(baos, jobRdfNode.getModel, RDFFormat.TURTLE_PRETTY)

      // scalastyle:off
//      val job = JenaPluginUtils.polymorphicCast(jobRdfNode, classOf[org.aksw.jena_sparql_api.conjure.dataset.algebra.Op])
      val job = JenaPluginUtils.polymorphicCast(jobRdfNode, classOf[Job])
      // scalastyle:on

      if (job == null) {
        throw new RuntimeException("op of workflow was null, workflow itself was: " + job)
      }

      // Set up the repo on the worker
      // TODO Test for race conditions
      //val repo = HttpResourceRepositoryFromFileSystemImpl.createDefault
      //val executor = new OpExecutorDefault(repo)
      val parser = SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes, false)
      val repo = HttpResourceRepositoryFromFileSystemImpl.createDefault
      val cacheStore = repo.getCacheStore
      //val catalogExecutor = new OpExecutorDefault(repo, new ConjureTaskContext(job, new HashMap[String, DataRef](), new HashMap[String, Model]()));

      val taskContexts: java.util.List[ConjureTaskContext] = taskContextIt.toList.asJava
      val dcatDatasets = MainCliConjureSimple.executeJob(taskContexts, job, repo, cacheStore)
      
      dcatDatasets.asScala.map(x => (x, true, "some data")).iterator

//      it.map(taskContextsIt => {
//        val taskContexts = taskContextsIt.l
//        r = MainCliConjureSimple.executeJob(taskContexts, job, repo, cacheStore);
//
//        //r
//        ("entry", true, "some data")
//      })

    })

    val stopwatch = Stopwatch.createStarted()
    val evalResult = executiveRdd.collect

    logger.info("RESULTS: ----------------------------")
    for (item <- evalResult) {
      logger.info("Result status: " + item)
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
