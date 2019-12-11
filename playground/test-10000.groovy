import org.aksw.conjure.cli.main.FactoryBeanObject
import org.aksw.jena_sparql_api.common.DefaultPrefixes
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefOp
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefUrl
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpData
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpUpdateRequest
import org.aksw.jena_sparql_api.conjure.fluent.ConjureBuilderImpl
import org.aksw.jena_sparql_api.conjure.fluent.ConjureContext
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.conjure.traversal.api.OpTraversalSelf
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl
import org.apache.jena.query.Syntax
import org.apache.jena.rdf.model.ModelFactory
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpDataRefResource
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpConstruct

// Useful information about bean definitions in groovy:
// https://spring.io/blog/2014/03/03/groovy-bean-configuration-in-spring-framework-4

url = "http://localhost/~raven/test.hdt"
ctx = new ConjureContext()
ctxModel = ctx.getModel()
cj = new ConjureBuilderImpl(ctx)


parser = SparqlStmtParserImpl.create(Syntax.syntaxARQ, DefaultPrefixes.prefixes, false)
model = ModelFactory.createDefaultModel()

//v = OpVar.create(model, "dataRef");
varName = "dataRef";
		
beans {
	dataref FactoryBeanObject, DataRefOp.create(
		OpConstruct.create(model, OpDataRefResource.from(model, DataRefUrl.create(model, "file:///home/raven/public_html/medium.nt")),
	    parser.apply("""
CONSTRUCT {
  ?a ?b ?c .
  ?c ?d ?e 
} {	    	
  { SELECT DISTINCT ?a {
    ?a dcat:distribution [
      dcat:byteSize ?byteSize
    ]
#    FILTER(?byteSize < 100000)
#  } LIMIT 1000 }
  } }

  ?a ?b ?c
  OPTIONAL { ?c ?d ?e }
}
""").toString()));

	job FactoryBeanObject, Job.create(ctxModel, "tripleCount")
		.setOp(
                   cj.coalesce(
			cj.fromVar(varName).hdtHeader().construct("CONSTRUCT WHERE { ?s <http://rdfs.org/ns/void#triples> ?o }"),
			cj.fromVar(varName).hdtHeader().construct("CONSTRUCT WHERE { ?s <http://purl.org/HDT/hdt#triplesnumTriples> ?o }"),
			cj.fromVar(varName).tripleCount().cache())
                  .construct("CONSTRUCT { ?s <http://rdfs.org/ns/void#triples> ?fix } { ?s <http://rdfs.org/ns/void#triples> ?o BIND(xsd:int(STR(?o)) AS ?fix) }")
                  .getOp())
		.addJobBinding("datasetId", OpTraversalSelf.create(ctxModel))
		;
}

