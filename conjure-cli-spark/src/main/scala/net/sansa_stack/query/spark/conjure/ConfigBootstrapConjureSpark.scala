package net.sansa_stack.query.spark.conjure

import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.ApplicationArguments
import org.aksw.conjure.cli.main.CommandMain
import com.beust.jcommander.JCommander
import java.nio.file.Path
import org.springframework.beans.factory.annotation.Value

@Configuration
class ConfigBootstrapConjureSpark {

  @Bean
  @Autowired
  def applicationRunner(catalogDataRef: DataRef, job: Job, conjureConfig: ConjureConfig): ApplicationRunner = {
    new ApplicationRunner {
      override def run(args: ApplicationArguments): Unit = {
        MainCliConjureSpark.mainSpark(null, catalogDataRef, job, conjureConfig)
      }
    }
  }

}
