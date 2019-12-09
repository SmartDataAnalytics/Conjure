package net.sansa_stack.query.spark.conjure

import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.aksw.conjure.cli.config.ConjureCliArgs
import org.aksw.conjure.cli.config.SpringSourcesConfig

@Configuration
class ConfigConjureSparkMaster {
  @Bean
  @Autowired
  def applicationRunner(catalogDataRef: DataRef, job: Job, cliArgs: ConjureCliArgs, sourcesConfig: SpringSourcesConfig): ApplicationRunner = {
    new ApplicationRunner {
      override def run(args: ApplicationArguments): Unit = {
        ConjureSparkUtils.mainSpark(cliArgs, catalogDataRef, job, sourcesConfig)
      }
    }
  }

}
