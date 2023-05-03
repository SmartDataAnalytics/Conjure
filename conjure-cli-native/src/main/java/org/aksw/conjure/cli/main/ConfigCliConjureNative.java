package org.aksw.conjure.cli.main;

import java.util.List;

import org.aksw.dcat.jena.domain.api.DcatDataset;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.RdfDataRef;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureConstants;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureFormatConfig;
import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.aksw.jenax.arq.util.node.NodeTransformLib2;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
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
            RdfDataRef catalogDataRef,
            Job job,
            ConjureFormatConfig formatConfig) {
        return args -> {
            List<DcatDataset> datasets = MainCliConjureNative.executeJob(catalogDataRef, job, formatConfig);

            Model model = ModelFactory.createDefaultModel();
            for(DcatDataset dataset : datasets) {
                Model contribModel = dataset.getModel();
                model.add(contribModel);
            }

            postProcessResultModel(model, job);

            RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);


//			RDFDataMgr.write(System.out, job.getModel(), RDFFormat.TURTLE_BLOCKS);
//			for(DcatDataset dataset : datasets) {
//				RDFDataMgr.write(System.out, dataset.getModel(), RDFFormat.TURTLE_BLOCKS);
//			}
        };
    }

    public static void postProcessResultModel(Model model, Job job) {
        model.add(job.getModel());

        NodeTransformLib2.applyNodeTransform(
                n -> ConjureConstants.PROV_PLACEHOLDER_NODE.equals(n) ? job.asNode() : n,
                model);
    }

}
