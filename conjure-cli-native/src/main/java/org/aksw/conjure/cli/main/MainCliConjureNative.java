package org.aksw.conjure.cli.main;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.conjure.cli.config.ConfigConjureSparkBase;
import org.aksw.conjure.cli.config.ConjureCliArgs;
import org.aksw.conjure.cli.config.SpringSourcesConfig;
import org.aksw.dcat.jena.domain.api.DcatDataset;
import org.aksw.jena_sparql_api.common.DefaultPrefixes;
import org.aksw.jena_sparql_api.conjure.datapod.api.RdfDataPod;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRef;
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefDcat;
import org.aksw.jena_sparql_api.conjure.dataset.algebra.Op;
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpDataRefResource;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ConjureFormatConfig;
import org.aksw.jena_sparql_api.conjure.dataset.engine.ExecutionUtils;
import org.aksw.jena_sparql_api.conjure.dataset.engine.OpExecutorDefault;
import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;
import org.aksw.jena_sparql_api.conjure.job.api.Job;
import org.aksw.jena_sparql_api.http.repository.api.HttpResourceRepositoryFromFileSystem;
import org.aksw.jena_sparql_api.http.repository.api.ResourceStore;
import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.aksw.jena_sparql_api.rx.SparqlRx;
import org.aksw.jena_sparql_api.stmt.SparqlStmt;
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl;
import org.aksw.jena_sparql_api.utils.Vars;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sparql.graph.NodeTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

@SpringBootApplication
public class MainCliConjureNative {
	public static String URL_SCHEME_FILE = "file://";

	
	private static final Logger logger = LoggerFactory.getLogger(MainCliConjureNative.class);

	public static CommandMain cm;

	public MainCliConjureNative() {
	}
	
	public static Map<String, byte[]> loadSources(Path basePath, Collection<String> sources) {
		Map<String, byte[]> result = new HashMap<>();
		for(String source : sources) {
			Path path = resolvePath(basePath, source);
			if(path != null) {
				try {
					byte[] content = Files.readAllBytes(path);
					result.put(source, content);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return result;
	}
	
	/**
	 * Write a sourceToContent map to physical files, and return a map with
	 * sourceToPath, where path is the path to the written file
	 * 
	 * @param baseFolder
	 * @param sourceToContent
	 * @return
	 * @throws IOException
	 */
	public static BiMap<String, Path> writeFiles(Path baseFolder, Map<String, byte[]> sourceToContent) throws IOException {
		BiMap<String, Path> result = HashBiMap.create();
		for(Entry<String, byte[]> e : sourceToContent.entrySet()) {
			String source = e.getKey();
			Path tmpPath = Paths.get(source);
			Path relPath = tmpPath.getFileName();

			byte[] content = e.getValue();

			Path absPath = baseFolder.resolve(relPath);
			logger.info("Writing file " + relPath + " with " + content.length + " to " + absPath);
			Files.createDirectories(absPath.getParent());
			Files.write(absPath, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			
			result.put(source, absPath);
		}
		
		return result;
	}
	
	
	public static Set<String> toFileUris(Collection<Path> paths) {
		Set<String> result = paths.stream()
			.map(MainCliConjureNative::toFileUri)
			.collect(Collectors.toSet());
		
		return result;
	}

	public static String toFileUri(Path path) {
		String result = path.toUri().toString();
		return result;
	}

//	public static String toFileUri(Path path) {
//		String result;
//		try {
//			result = path.toUri().toURL().toString();
//		} catch (MalformedURLException e) {
//			throw new RuntimeException(e);
//		}
//
//		return result;
//	}

	public static URL resolveOnClassPath(ClassLoader classLoader, String path) {
		URL result = classLoader.getResource(path);
		if(result != null) {
			try(InputStream in = result.openStream()) {
	
			} catch (IOException e) {
				result = null;
			}
		}

		return result;
	}
	
	
//	public static Path urlAsPath(Path basePath, String arg) {
//		Path result =
//				arg.startsWith(URL_SCHEME_FILE) ? Paths.get(arg.substring(URL_SCHEME_FILE.length())) :
//				arg.startsWith("/") ? Paths.get(arg) :
//				null;
//
//		return result;
//	}
	
	// We may need canonicalization to make cli arg handling and spring interop nicer
	public static String canonicalizeSource(Path basePath, String arg) {
		Path path = resolvePath(basePath, arg);
		String result = path == null ? arg : toFileUri(path);
		return result;
	}
	
	public static Path resolvePath(Path basePath, String arg) {

		Path result =
				arg.startsWith(URL_SCHEME_FILE) ? Paths.get(arg.substring(URL_SCHEME_FILE.length())) :
				arg.startsWith("/") ? Paths.get(arg) :
				resolveOnClassPath(MainCliConjureNative.class.getClassLoader(), arg) != null ? null :
				arg.contains(":/") ? null : // URL-like arguments of any kind
				basePath.resolve(arg);
				
		return result;
	}
	
	public static Op loadConjureJob(String fileOrUri) {
		Model model = RDFDataMgr.loadModel(fileOrUri);
		List<Op> ops = model.listSubjects().mapWith(x -> JenaPluginUtils.polymorphicCast(x, Op.class))
				// .filter(op -> op.getParent()) // TODO Find the root
				.toList();

		// Expect 1 result
		Op result = ops.iterator().next();

		return result;
	}

	public static void main(String[] args) throws Exception {
//		if(true) {
//			System.out.println(MainCliConjureNative.toFileUri(Paths.get("test")));
//			return;
//		}
		
		ConjureCliArgs cliArgs = ConjureCliArgs.parse(args);

		if (cliArgs.getCm().help) {
			cliArgs.getJcommander().usage();
			return;
		}

		SpringSourcesConfig config = ConfigConjureSparkBase.parseArgs(cliArgs);
		Set<String> sources = config.getSources();
		
		SpringApplication app = new SpringApplicationBuilder()
			.sources(ConfigConjureSparkBase.class, ConfigCliConjureNative.class)
			.bannerMode(Banner.Mode.OFF)
			.headless(false)
			.web(WebApplicationType.NONE)
			.build();

		app.setSources(sources);
		
		try (ConfigurableApplicationContext ctx = app.run(args)) {
		}
	}

	
	public static RDFNode clone(RDFNode rdfNode) {
		Model clone = ModelFactory.createDefaultModel();
		clone.add(rdfNode.getModel());
		RDFNode result = rdfNode.inModel(clone);
		return result;
	}
	
	public static List<TaskContext> createTasksContexts(DataRef catalogDataRef, Job job,
			HttpResourceRepositoryFromFileSystem repo,
			RDFFormat catalogFormat) throws Exception {

		//RDFFormat rdfFormat = formatConfig.getCatalogFormat();
		
		// Create a copy of data catalogDataRef to prevent changing it
		DataRef clone = JenaPluginUtils.polymorphicCast(clone(catalogDataRef), DataRef.class);
		
		Op catalogWorkflow = OpDataRefResource.from(clone.getModel(), clone);

//	    String origHash = ResourceTreeUtils.createGenericHash(conjureWorkflow);
//	    String coreHash = ResourceTreeUtils.createGenericHash(coreOp);

		// Op catalogCreationWorkflow = job.getOp();

		Function<String, SparqlStmt> parser = SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes,
				false);
		// HttpResourceRepositoryFromFileSystemImpl repo =
		// HttpResourceRepositoryFromFileSystemImpl.createDefault();
		// ResourceStore cacheStore = repo.getCacheStore();
		OpExecutorDefault catalogExecutor = new OpExecutorDefault(repo,
				new TaskContext(job, new HashMap<>(), new HashMap<>()), new HashMap<>(), catalogFormat);

		String queryStr = "CONSTRUCT {\n"
						+ "        ?a ?b ?c .\n"
						+ "        ?c ?d ?e\n" + "      } {\n"
						+ "\n"
						+ "        { SELECT DISTINCT ?a {\n"
						+ "          ?a dcat:distribution [\n"
						+
//				"            dcat:byteSize ?byteSize\n" + 
						"          ]\n"
						+ "        } }\n"
						+ "\n"
						+ "        ?a ?b ?c\n"
						+ "        OPTIONAL { ?c ?d ?e }\n" + "}";

		Query dcatQuery = parser.apply(queryStr).getAsQueryStmt().getQuery();

		List<TaskContext> taskContexts = new ArrayList<>();
		// List<Resource> inputRecords;
//		try(RdfDataObject catalog = DataObjects.fromSparqlEndpoint("https://databus.dbpedia.org/repo/sparql", null, null)) {			
		try (RdfDataPod catalog = catalogWorkflow.accept(catalogExecutor)) {
			try (RDFConnection conn = catalog.openConnection()) {

				List<Resource> catalogRecords = SparqlRx.execConstructGrouped(conn, Vars.a, dcatQuery)
						.map(RDFNode::asResource).toList().blockingGet();

				// For every input record is a dcat entry, assign an anonymous dataref
				for (Resource catalogRecord : catalogRecords) {
					Map<String, DataRef> nameToDataRef = new HashMap<>();

					Query q = parser.apply("SELECT DISTINCT ?x { ?x dcat:distribution [] }").getQuery();
					Model m = catalogRecord.getModel();

					// QueryExecution qe =

					List<Resource> dcatDataRefs = SparqlRx.execSelect(() -> QueryExecutionFactory.create(q, m))
							.map(qs -> qs.get("x")).map(RDFNode::asResource).toList().blockingGet();

					int i = 0;
					for (Resource r : dcatDataRefs) {
						Model xxmodel = ModelFactory.createDefaultModel();
						xxmodel.add(r.getModel());
						r = r.inModel(xxmodel);

						DataRefDcat dr = DataRefDcat.create(xxmodel, r);

						// TODO Add option whether to log the input record
						// RDFDataMgr.write(System.err, dr.getModel(), RDFFormat.TURTLE_PRETTY);

						nameToDataRef.put("unnamedDataRef" + (i++), dr);
					}

					logger.info("Registered data refs for input " + catalogRecord + " are: " + nameToDataRef);
					Map<String, Model> nameToModel = new HashMap<>();
					nameToModel.put("http://input", catalogRecord.getModel());

					TaskContext taskContext = new TaskContext(catalogRecord, nameToDataRef, nameToModel);
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

		return taskContexts;

	}

	public static List<DcatDataset> executeJob(
			DataRef catalogDataRef,
			Job job,
			ConjureFormatConfig formatConfig) throws Exception {
		
		RDFFormat catalogFormat = formatConfig.getCatalogFormat();
		
		// Function<String, SparqlStmt> parser =
		// SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes,
		// false);
		HttpResourceRepositoryFromFileSystemImpl repo = HttpResourceRepositoryFromFileSystemImpl.createDefault();
		ResourceStore cacheStore = repo.getCacheStore();
		// OpExecutorDefault catalogExecutor = new OpExecutorDefault(repo, new
		// TaskContext(job, new HashMap<>(), new HashMap<>()));

		List<TaskContext> taskContexts = createTasksContexts(catalogDataRef, job, repo, catalogFormat);
		
		List<DcatDataset> result = taskContexts.stream()
				.map(taskContext -> ExecutionUtils.executeJob(job, taskContext, repo, cacheStore, formatConfig))
				.collect(Collectors.toList());
		return result;
	}

//	public static List<DcatDataset> executeJob(List<TaskContext> taskContexts, Job job,
//			HttpResourceRepositoryFromFileSystem repo, ResourceStore cacheStore) throws Exception {
//
//		// Check the contexts for well-known data refs; i.e. dcat entries
//		// Ready for workflow execution!
//
////		logger.info("Retrieved " + inputRecords.size() + " contexts for processing " + inputRecords);
//
//		List<DcatDataset> resultDatasets = ExecutionUtils.executeJob(job, taskContexts, repo, cacheStore);
//		return resultDatasets;
////		for(DcatDataset resultDataset : resultDatasets) {
////			DcatDataset closure = resultDataset.inModel(ResourceUtils.reachableClosure(resultDataset)).as(DcatDataset.class);
////			RDFDataMgrEx.execSparql(closure.getModel(), "replacens.sparql", ImmutableMap.<String, String>builder()
////					.put("SOURCE_NS", cm.repoBase)
////					.put("TARGET_NS", cm.webBase)
////					.build()::get);
////
////			RDFDataMgr.write(System.out, closure.getModel(), RDFFormat.TURTLE_PRETTY);
////		}
//	}
	
	public static Path resolveLocalUncFileUrl(String str, Set<String> localHostNames) {
		Path result = null;
		if(str.startsWith(URL_SCHEME_FILE)) {
			URL url = null;
			try {
				url = new URL(str);
			} catch (MalformedURLException e) {
				logger.warn("Invalid URL", e);
			}
			
			if(url != null) {
				String host = url.getHost();
				if(localHostNames.contains(host)) {
					String pathStr = url.getPath();
					result = Paths.get(pathStr);
				}
			}			
		}
		
		return result;
	}
	
	public static Path stringToPath(String str) {
		Path result = str.startsWith(URL_SCHEME_FILE) ? Paths.get(str.substring(URL_SCHEME_FILE.length())) : null;
		return result;
	}

	public static NodeTransform asNodeTransform(Function<Path, String> pathToIri) {
		return o -> {
			String str = o.isURI() ? o.getURI() : o.isLiteral() ? o.getLiteralLexicalForm() : null;

			Path path = str == null ? null : stringToPath(str);
			String iri = path == null ? null : pathToIri.apply(path);
			Node r = iri == null ? o : NodeFactory.createURI(iri);
			return r;
		};
	}

//	public void replaceNs(Model model, Function<Path, String> pathToIri) {
//		StmtIterator it = model.listStatements();
//		while(it.hasNext()) {
//			Statement stmt = it.next();
//			RDFNode o = stmt.getObject();
//			
//		}
//		
//		for(DcatDataset resultDataset : resultDatasets) {
//			DcatDataset closure = resultDataset.inModel(ResourceUtils.reachableClosure(resultDataset)).as(DcatDataset.class);
//			RDFDataMgrEx.execSparql(closure.getModel(), "replacens.sparql", ImmutableMap.<String, String>builder()
//					.put("SOURCE_NS", cm.repoBase)
//					.put("TARGET_NS", cm.webBase)
//					.build()::get);
//
//			RDFDataMgr.write(System.out, closure.getModel(), RDFFormat.TURTLE_PRETTY);
//		}
//
//	}
}
