package org.aksw.conjure.cli.main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.jena_sparql_api.common.DefaultPrefixes;
import org.aksw.jena_sparql_api.conjure.datapod.api.RdfDataPod;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefDcat;
import org.aksw.jena_sparql_api.conjure.dataset.algebra.Op;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ExecutionUtils;
import org.aksw.jena_sparql_api.conjure.dataset.engine.OpExecutorDefault;
import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;
import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.aksw.jena_sparql_api.conjure.plugin.JenaPluginConjure;
import org.aksw.jena_sparql_api.http.repository.api.HttpResourceRepositoryFromFileSystem;
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.aksw.jena_sparql_api.rx.SparqlRx;
import org.aksw.jena_sparql_api.stmt.SparqlStmt;
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl;
import org.aksw.jena_sparql_api.utils.Vars;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sys.JenaSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.beust.jcommander.JCommander;



@SpringBootApplication
public class MainCliConjure {
	private static final Logger logger = LoggerFactory.getLogger(MainCliConjure.class);
	
	
	public static CommandMain cm;
	
	
	public MainCliConjure() {
	}	
	
	public static Op loadConjureJob(String fileOrUri) {
		Model model = RDFDataMgr.loadModel(fileOrUri);
		List<Op> ops = model.listSubjects()
			.mapWith(x -> JenaPluginUtils.polymorphicCast(x, Op.class))
			//.filter(op -> op.getParent()) // TODO Find the root
			.toList();
		
		// Expect 1 result
		Op result = ops.iterator().next();
		
		return result;
	}
	
	
	public static void main(String[] args) throws Exception {
		JenaSystem.init();
		JenaPluginConjure.init();
		
		cm = new CommandMain();
//		CommandShow cmShow = new CommandShow();

		
		// CommandCommit commit = new CommandCommit();
		JCommander jc = JCommander.newBuilder()
				.addObject(cm)
//				.addCommand("show", cmShow)
				.build();

		jc.parse(args);

        if (cm.help) {
            jc.usage();
            return;
        }

        if(cm.inputModelFile == null) {
        	throw new RuntimeException("No input (catalog) model provided");
        }

        Model inputModel = RDFDataMgr.loadModel(cm.inputModelFile);

        // TODO Extend to multiple files
        List<Resource> jobModels = cm.nonOptionArgs.stream()
        		.map(x -> ResourceFactory.createResource())
        		.collect(Collectors.toList());

		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder()
				.sources(ConfigGroovy.class, ConfigCliConjure.class)
				.bannerMode(Banner.Mode.OFF)
				// If true, Desktop.isDesktopSupported() will return false, meaning we can't
				// launch a browser
				.headless(false)
				.web(WebApplicationType.NONE)
				.run(args)) {
		}

        // ApplicationContext ctx = 
        //ConfigurableApplicationContext ctx = SpringApplication.run(new Class<?>[] {ConfigGroovy.class, MainCliConjure.class}, args);
//        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();
//        System.out.println("Bean factory: " + beanFactory);
//        Job job = (Job)ctx.getBean("job");
//        logger.info("Job is: " + job);
        
        //Job job = null;
        //executeJob(job);
//		HttpResourceRepositoryFromFileSystem repo = HttpResourceRepositoryFromFileSystemImpl.createDefault();		
//		OpExecutorDefault catalogExecutor = new OpExecutorDefault(repo, null);

		//ExecutionUtils.executeJob(job, repo, taskContexts);

        
        
        
		//Op conjureWorkflow = JenaPluginUtils.polymorphicCast(deserializedWorkflowRes, Op.class);

        
//		Model workflowModel = RDFDataMgr.loadModel(tmpFile.toString());		
//		Resource deserializedWorkflowRes = deserializedWorkflowModel.createResource(workflowUri);

        
        //cm.nonOptionArgs

		//JCommander deploySubCommands = jc.getCommands().get("deploy");

		// Inputs:
		// catalog kb (background knowledge)
		// catalog selector (set of resources in the catalog)
		// process template

		
//		String processTemplate;
		//Model deserializedWorkflowModel = RDFDataMgr.loadModel(tmpFile.toString());		

	}
	
	
	public static void executeJob(Job job) throws Exception {
		Op basicWorkflow = job.getOp();
		
		Function<String, SparqlStmt> parser = SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes, false);
		HttpResourceRepositoryFromFileSystem repo = HttpResourceRepositoryFromFileSystemImpl.createDefault();		
		OpExecutorDefault catalogExecutor = new OpExecutorDefault(repo, null);

		
		
		String queryStr = "CONSTRUCT {\n" + 
				"        ?a ?b ?c .\n" + 
				"        ?c ?d ?e\n" + 
				"      } {\n" + 
				"\n" + 
				"        { SELECT DISTINCT ?a {\n" + 
				"          ?a dcat:distribution [\n" + 
//				"            dcat:byteSize ?byteSize\n" + 
				"          ]\n" + 
				"        } LIMIT 10 }\n" + 
				"\n" + 
				"        ?a ?b ?c\n" + 
				"        OPTIONAL { ?c ?d ?e }\n" + 
				"}";
		
		Query dcatQuery = parser.apply(queryStr).getAsQueryStmt().getQuery();

		
		List<TaskContext> taskContexts = new ArrayList<>();
		//List<Resource> inputRecords;
//		try(RdfDataObject catalog = DataObjects.fromSparqlEndpoint("https://databus.dbpedia.org/repo/sparql", null, null)) {			
		try(RdfDataPod catalog = basicWorkflow.accept(catalogExecutor)) {			
			try(RDFConnection conn = catalog.openConnection()) {
				
	    	    List<Resource> inputRecords = SparqlRx.execConstructGrouped(conn, Vars.a, dcatQuery)
		    	        .map(RDFNode::asResource)
	    	    		.toList()
	    	    		.blockingGet();

	    		// For every input record is a dcat entry, assign an anonymous dataref
	    		for(Resource inputRecord : inputRecords) {
	    			Map<String, DataRef> nameToDataRef = new HashMap<>();

	    			Query q = parser.apply("SELECT DISTINCT ?x { ?x dcat:distribution [] }").getQuery();
	    			Model m = inputRecord.getModel();

	    			// QueryExecution qe = 

	    			List<Resource> dcatDataRefs = SparqlRx.execSelect(() -> QueryExecutionFactory.create(q, m))
	    	        	.map(qs -> qs.get("x"))
	    				.map(RDFNode::asResource)
	    	        	.toList()
	    	        	.blockingGet();

	    			int i = 0;
	    			for(Resource r : dcatDataRefs) {
	    				Model xxmodel = ModelFactory.createDefaultModel();
	    				xxmodel.add(r.getModel());
	    				r = r.inModel(xxmodel);

	    				DataRefDcat dr = DataRefDcat.create(xxmodel, r);
	    				
	    				RDFDataMgr.write(System.err, dr.getModel(), RDFFormat.TURTLE_PRETTY);
	    				
	    				nameToDataRef.put("unnamedDataRef" + (i++), dr);
	    			}
	    			
		    		logger.info("Registered data refs for input " + inputRecord + " are: " + nameToDataRef);
	    			Map<String, Model> nameToModel = new HashMap<>();
	    			nameToModel.put("http://input", inputRecord.getModel());
		    		
		    		TaskContext taskContext = new TaskContext(inputRecord, nameToDataRef, nameToModel);
	    			taskContexts.add(taskContext);
	    			// Note, that the dcat ref query was run on the inputContext models
	    			// So the following assertion is assumed to hold:
	    			// dcatDataRef.getModel() == inputRecord.getModel()
	    		}

	    		logger.info("Created " + taskContexts.size() + " task contexts");
	    		
//	    		if(true) {
//	    			return;
//	    		}

//				urls = SparqlRx.execSelect(conn,
////						"SELECT DISTINCT ?o { ?s <http://www.w3.org/ns/dcat#downloadURL> ?o } LIMIT 10")
//						parser.apply("SELECT DISTINCT ?o { ?s dataid:group ?g ; dcat:distribution/dcat:downloadURL ?o } LIMIT 10")
//							.getAsQueryStmt().getQuery())
//					.map(qs -> qs.get("o"))
//					.map(RDFNode::toString)
//					.toList()
//					.blockingGet();				
			}			
		}

		
		// Check the contexts for well-known data refs; i.e. dcat entries
		
		
		
		
		// Ready for workflow execution!

//		logger.info("Retrieved " + inputRecords.size() + " contexts for processing " + inputRecords);
		
		ExecutionUtils.executeJob(job, repo, taskContexts);

	}
}
