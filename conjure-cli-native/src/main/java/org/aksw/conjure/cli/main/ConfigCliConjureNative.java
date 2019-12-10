package org.aksw.conjure.cli.main;

import java.util.List;

import org.aksw.dcat.jena.domain.api.DcatDataset;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureFormatConfig;
import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigCliConjureNative {
	
//	@Bean
//	public Object applicationRunner() {
//		System.out.println("Test");
//		return 1;
//	}

	@Bean
	@Autowired
	public ApplicationRunner applicationRunner(
			DataRef catalogDataRef,
			Job job,
			ConjureFormatConfig formatConfig) {
		return args -> {
			List<DcatDataset> datasets = MainCliConjureNative.executeJob(catalogDataRef, job, formatConfig);
			
			for(DcatDataset dataset : datasets) {
				RDFDataMgr.write(System.out, dataset.getModel(), RDFFormat.TURTLE_PRETTY);
			}
		};
	}

}
