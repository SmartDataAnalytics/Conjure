package net.sansa_stack.query.spark.conjure

import java.io.File
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.Collections

import org.aksw.conjure.cli.config.ConjureCliArgs
import org.aksw.conjure.cli.config.ConjureProcessor
import org.aksw.conjure.cli.config.SpringSourcesConfig
import org.aksw.conjure.cli.main.CommandMain
import org.aksw.conjure.cli.main.MainCliConjureNative
import org.aksw.dcat.ap.utils.DcatUtils
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.http.domain.api.RdfEntityInfo
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl
import org.apache.jena.ext.com.google.common.base.StandardSystemProperty
import org.apache.jena.ext.com.google.common.base.Stopwatch
import org.apache.jena.ext.com.google.common.collect.ImmutableRangeSet
import org.apache.jena.ext.com.google.common.collect.Range
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
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
import scala.collection.JavaConverters.asScalaBufferConverter
import org.apache.jena.sparql.graph.NodeTransformLib
import org.aksw.jena_sparql_api.transform.result_set.QueryExecutionTransformResult
import org.apache.jena.sparql.graph.NodeTransform

object ConjureSparkUtils extends LazyLogging {

  def createPartitionKey(dcatDataset: Resource): String = {
    val key = Option(DcatUtils.getFirstDownloadUrl(dcatDataset)).getOrElse("")

    return key
  }

  def mainSpark(
      cliArgs: ConjureCliArgs,
      catalogDataRef: DataRef,
      job: Job,
      sourcesConfig: SpringSourcesConfig,
      catalogFormat: RDFFormat): Unit = { /* args: Array[String]): Unit = { */

    // val sourcePathToContent: java.util.Map[Path, Array[Byte]] = conjureConfig.getSourcePathToContent

    logger.info("Setting up spark for conjure job execution")

    // val catalogUrl = if (args.length == 0) "http://localhost/~raven/conjure.test.dcat.ttl" else args(0)

    val cm = cliArgs.getCm

    // val limit = if (args.length > 1) args(1).toInt else 10
    val numThreads = cm.numThreads
    // val sparkMaster = cm.sparkMaster + "[" + cm.numThreads + "]"
    val sparkMaster = cm.sparkMaster.trim

    // Fix for a spark issue with non-existing directory
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

    val masterHostName = InetAddress.getLocalHost.getHostName

    val builder = SparkSession.builder

    if (!sparkMaster.isEmpty) {
      val tmp = if (sparkMaster.toLowerCase.equals("local"))
        { sparkMaster + "[" + numThreads + "]" } else { sparkMaster }

      builder.master(tmp)
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

    val configBroadcast: Broadcast[SpringSourcesConfig] =
      sparkSession.sparkContext.broadcast(sourcesConfig)

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
    val initialTaskContexts = MainCliConjureNative.createTasksContexts(
        catalogDataRef, job, repo, catalogFormat).asScala

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

      val sourceToPath = MainCliConjureNative.writeFiles(tmpPath, sourcesConfig.getSourceToContent)
      val effectiveSources = SpringSourcesConfig.effectiveSources(config.getSources, sourceToPath)

      println("HOST " + hostname + " Serialized source files: " + sourceToPath)
      println("HOST " + hostname + " Effective file sources: " + effectiveSources)

      // New set up the spring app for this partition

      //      val properties = ImmutableMap.builder[String, Object]
      //        .put("task", sourcePathToContent)
      //        .build

      val app: SpringApplication = new SpringApplicationBuilder()
        // .properties(properties)
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

    val stopwatch = Stopwatch.createStarted
    val evalResult = resultCatalogRdd.collect

    val retrieveFiles = true

    // Broadcast the catalog to all workers
    if(retrieveFiles) {
      // TODO Assemble the catalog into a single model
      val fullCatalog = evalResult.map(x => x.getDcatRecord).toSeq

      // Remove all files that are in the local repository
      val catalog = fullCatalog
        .filter(dcatDataset => {
            val downloadUrl = DcatUtils.getFirstDownloadUrl(dcatDataset)
            val entityPath = MainCliConjureNative.resolveLocalUncFileUrl(downloadUrl, Collections.singleton(masterHostName))
            val r = repo.getEntityForPath(entityPath) == null
            // logger.info("Status " + r + " for " + downloadUrl + " on " + masterHostName)
            r
        })
        .toSeq

      val fullCatalogSize = fullCatalog.size
      val retrievalCatalogSize = catalog.size
      val localFileSize = fullCatalogSize - retrievalCatalogSize

      logger.info("Retrieving " + retrievalCatalogSize + " remote files out of " + fullCatalogSize + " (" + localFileSize + " found locally)")

      val catalogBroadcast: Broadcast[Seq[Resource]] =
        sparkSession.sparkContext.broadcast(catalog)

      val locations = workerNodeHostNames.map(x => (x, Seq(x)))
      val fileRetrievalRdd = sparkSession.sparkContext
        .makeRDD(locations)
        .coalesce(numPartitions)

      val itFile: Iterator[(String, String, Resource, Array[Byte])] = fileRetrievalRdd
        .mapPartitions(itHostName => {

        val workerHostName = InetAddress.getLocalHost.getHostName
        val catalog = catalogBroadcast.value

        logger.info("Commencing file retrieval on worker " + workerHostName)

        val workerRepo = HttpResourceRepositoryFromFileSystemImpl.createDefault();

        itHostName.flatMap(hostName => {
          var r: (String, String, Resource, Array[Byte]) = null
          // TODO Skip if hostName is that of the master

          // Filter the catalog to those entries that are on the worker
          catalog.map(dcatRecord => {
            // TODO We may want to check all download Urls for whether they are present on the server
            val downloadUrl = DcatUtils.getFirstDownloadUrl(dcatRecord)
            val entityPath = MainCliConjureNative.resolveLocalUncFileUrl(downloadUrl, Collections.singleton(workerHostName))

            logger.info("Mapped " + downloadUrl + " to " + entityPath)
            if(entityPath != null) {
              val entity = workerRepo.getEntityForPath(entityPath)
              val relPath = entity.getResource.getRelativePath.getParent.toString // .resolve(entity.getRelativePath).toString()
              logger.info("Mapped " + entityPath + " to " + relPath)
              // val entity = if (entities.isEmpty()) { null } else { entities.iterator.next }

              // logger.info("On worker " + hostName + ": " + entities.size() + " entities for " + downloadUrl)

              if(entity != null) {
                val info = entity.getCombinedInfo
                val path = entity.getAbsolutePath
                val byteContent = Files.readAllBytes(path);
                r = (downloadUrl, relPath, info, byteContent)
              }
            }
            r
            /*
            // TODO Add sanity check to ensure there is at least one download URL in the dcat record
            if (downloadUrl != null) {
              // Try to resolve the url to a path on the current host

              // TODO We should introduce some 'string to path mapper'
              val path = MainCliConjureNative.stringToPath(downloadUrl, Collections.singletonSet(workerHostName))
              if (path != null) {
                val byteContent = Files.readAllBytes(path);
                (downloadUrl, byteContent)
              } else {
                null
              }
            }
            */
          })
          .filter(x => x != null)
        })
        // Get the catalog broadcast and return all files on the current host
      })
      .toLocalIterator

      val urlMap = new java.util.HashMap[Node, Node]
      for((uri, relPath, rawInfo, content) <- itFile) {
        val targetStore = repo.getCacheStore
        // TODO Extend the store with a put method that can stream the content
        val tmpPath = Files.createTempFile("download-", ".dat")
        Files.write(tmpPath, content)

        val info = rawInfo.as(classOf[RdfEntityInfo])
        // downloadStore.put(uri, )
        val newEntity = targetStore.putWithMove(relPath, info, tmpPath)
        val newAbsPath = newEntity.getAbsolutePath

        // Update the download URL in the catalog with the new entity
        // val publicBaseIri = "http://" + hostName + "/"
        val newUrl = MainCliConjureNative.toFileUri(newAbsPath)

        urlMap.put(NodeFactory.createURI(uri), NodeFactory.createURI(newUrl))
      }

      logger.info("UrlMap: " + urlMap)

      val nodeTransform = new NodeTransform() {
        override def apply(node: Node): Node = {
          urlMap.getOrDefault(node, node)
        }
      }

      // Update the catalog items based on the url map
      for(item <- evalResult) {
        QueryExecutionTransformResult.applyNodeTransform(nodeTransform, item.getDcatRecord)
      }
    }

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
