package net.sansa_stack.query.spark.conjure

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

import org.aksw.conjure.cli.main.MainCliConjureNative
import org.aksw.jena_sparql_api.common.DefaultPrefixes
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureFormatConfig
import org.aksw.jena_sparql_api.conjure.dataset.engine.ExecutionUtils
import org.aksw.jena_sparql_api.conjure.dataset.engine.{ TaskContext => ConjureTaskContext }
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl
import org.aksw.jena_sparql_api.transform.result_set.QueryExecutionTransformResult
import org.apache.jena.query.Syntax
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.aksw.conjure.cli.config.ConjureProcessor
import org.aksw.conjure.cli.config.ConjureResult
import scala.compat.java8.FunctionConverters._
import org.aksw.dcat.ap.utils.DcatUtils

@Configuration
class ConfigConjureSparkProcessor {

  @Bean
  @Autowired
  def applicationRunner(): ApplicationRunner = {
    new ApplicationRunner {
      override def run(args: ApplicationArguments): Unit = {
        // MainCliConjureSpark.mainSpark(null, catalogDataRef, job)
      }
    }
  }

  @Bean
  @Autowired
  def conjureFormatConfig(): ConjureFormatConfig = {
    val r = new ConjureFormatConfig
    r
  }

  // configsBroadcast: Broadcast[java.util.Map[Path, Array[Byte]]]
  @Bean
  @Autowired
  def conjureProcessor(job: Job, formatConfig: ConjureFormatConfig): ConjureProcessor = {

    // val jobRdfNode: Resource = null;

    // val jobRdfNode = jobBroadcast.value;
    // val baos = new ByteArrayOutputStream
    // RDFDataMgr.write(baos, jobRdfNode.getModel, RDFFormat.TURTLE_PRETTY)

    // scalastyle:off
    //      val job = JenaPluginUtils.polymorphicCast(jobRdfNode, classOf[org.aksw.jena_sparql_api.conjure.dataset.algebra.Op])
    // val job = JenaPluginUtils.polymorphicCast(jobRdfNode, classOf[Job])
    // scalastyle:on

    if (job == null) {
      throw new RuntimeException("op of workflow was null, workflow itself was: " + job)
    }

    // Set up the repo on the worker
    // TODO Test for race conditions
    // val repo = HttpResourceRepositoryFromFileSystemImpl.createDefault
    // val executor = new OpExecutorDefault(repo)
    val parser = SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes, false)
    val repoPath = HttpResourceRepositoryFromFileSystemImpl.getDefaultPath;
    val repo = HttpResourceRepositoryFromFileSystemImpl.createDefault
    val cacheStore = repo.getCacheStore
    // val catalogExecutor = new OpExecutorDefault(repo, new ConjureTaskContext(job, new HashMap[String, DataRef](), new HashMap[String, Model]()));

    val hostName = InetAddress.getLocalHost.getHostName
    val publicBaseIri = "http://" + hostName + "/"
    val pathToUri: Path => String = p => {
      // println("GOT PATHS " + p + " - " + repoPath)
      // publicBaseIri + repoPath.relativize(p).toString()
      "file://" + hostName + p.toAbsolutePath().toString()
    }
    val nodeTransform = MainCliConjureNative.asNodeTransform(pathToUri.asJava)

    val result = new ConjureProcessor() {
      def process(taskContext: ConjureTaskContext): ConjureResult = {
        // val taskContexts: java.util.List[ConjureTaskContext] = taskContextIt.toList.asJava
        val dcatDataset = ExecutionUtils.executeJob(job, taskContext, repo, cacheStore, formatConfig)

        // My impression is that transmitting data with a separate job is more efficient:
        // We can use rdd.collect() to create the conjure result catalog
        // and then use rdd.toLocalIterator() only for the dataset file retrieval
        val yieldData = false
        var fileMap: java.util.Map[String, Array[Byte]] = null
        if(yieldData) {
          // Load the referenced dcatData into the result
          val url = DcatUtils.getFirstDownloadUrl(dcatDataset)
          val absPath = MainCliConjureNative.stringToPath(url)
          val byteContent = Files.readAllBytes(absPath);
          val relPath = repoPath.relativize(absPath).toString

          fileMap.put(relPath, byteContent)
        }

        val x = QueryExecutionTransformResult.applyNodeTransform(nodeTransform, dcatDataset)

        val r = new ConjureResult(x.asResource(), true, "", fileMap)
        // r.dcatRecord = x.asResource
        // r.message = ""
        // r.success = true
        r
      }
    }

    result
  }
}
