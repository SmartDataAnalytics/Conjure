package org.aksw.conjure.cli.main;

import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureFormatConfig;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ExecutionUtils;
import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigCliConjureId {
	
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
			String jobId = ExecutionUtils.createDefaultJobHash(job);

			Model m = ModelFactory.createDefaultModel();
			Resource r = m.createResource();
			r
				.addProperty(RDF.type, ConjureVocab.ConjureJobMapping)
				.addLiteral(DCTerms.identifier, job.getJobName())
				.addProperty(ConjureVocab.hash, jobId);
					
			RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
		};
	}

}
