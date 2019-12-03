package net.sansa_stack.query.spark.conjure

import com.typesafe.scalalogging.LazyLogging
import org.aksw.conjure.cli.main.MainCliConjureSimple
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.setAsJavaSetConverter

object MainCliConjureSpark extends LazyLogging {

  def main(args: Array[String]): Unit = {
    val conjureCliArgs = ConjureCliArgs.parse(args)
    val conjureConfig = ConfigConjureSparkBase.parseArgs(conjureCliArgs)

    val sourcePaths = conjureConfig.getSourcePathToContent.keySet
    val effectiveSources = sourcePaths.asScala.map(e => MainCliConjureSimple.toFileUri(e)).toSet.asJava

    val app: SpringApplication = new SpringApplicationBuilder()
      .sources(classOf[ConfigConjureSparkBase], classOf[ConfigConjureSparkMaster])
      .bannerMode(Banner.Mode.OFF)
      // If true, Desktop.isDesktopSupported() will return false, meaning we can't
      // launch a browser
      .headless(false)
      .web(WebApplicationType.NONE)
      .build()

    app.setSources(effectiveSources)

    val ctx: ConfigurableApplicationContext = app.run(args: _*)
  }
}
