package org.aksw.conjure.cli.main;

import java.util.Set;

import org.aksw.conjure.cli.config.ConfigConjureSparkBase;
import org.aksw.conjure.cli.config.ConjureCliArgs;
import org.aksw.conjure.cli.config.SpringSourcesConfig;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class MainCliConjureId {
	public static void main(String[] args) {
		ConjureCliArgs cliArgs = ConjureCliArgs.parse(args);

		if (cliArgs.getCm().help) {
			cliArgs.getJcommander().usage();
			return;
		}

		SpringSourcesConfig config = ConfigConjureSparkBase.parseArgs(cliArgs);
		Set<String> sources = config.getSources();
		
		SpringApplication app = new SpringApplicationBuilder()
			.sources(ConfigConjureSparkBase.class, ConfigCliConjureId.class)
			.bannerMode(Banner.Mode.OFF)
			.headless(false)
			.web(WebApplicationType.NONE)
			.build();

		app.setSources(sources);
		
		try (ConfigurableApplicationContext ctx = app.run(args)) {
		}
		
	}
}
