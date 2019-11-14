package org.aksw.conjure.cli.main;

import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigCliConjure {
	
//	@Bean
//	public Object applicationRunner() {
//		System.out.println("Test");
//		return 1;
//	}

	@Bean
	@Autowired
	public ApplicationRunner applicationRunner(Job job) {
		return args -> {
			MainCliConjure.executeJob(job);
		};
	}

}
