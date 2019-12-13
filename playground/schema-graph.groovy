import org.aksw.conjure.cli.main.FactoryBeanObject
import org.aksw.jena_sparql_api.common.DefaultPrefixes
import org.aksw.jena_sparql_api.conjure.dataref.rdf.api.DataRefOp
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpData
import org.aksw.jena_sparql_api.conjure.dataset.algebra.OpUpdateRequest
import org.aksw.jena_sparql_api.conjure.fluent.ConjureBuilderImpl
import org.aksw.jena_sparql_api.conjure.fluent.ConjureContext
import org.aksw.jena_sparql_api.conjure.job.api.Job
import org.aksw.jena_sparql_api.conjure.traversal.api.OpTraversalSelf
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl
import org.apache.jena.query.Syntax
import org.apache.jena.rdf.model.ModelFactory

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
		OpUpdateRequest.create(model, OpData.create(model),
	    parser.apply("INSERT DATA { " +
			"<http://mytestdata> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <file:///home/raven/public_html/conjure.test.dcat.ttl> ] ." +
            "<http://limbocat> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <file:///home/raven/Projects/limbo/git/metadata-catalog/catalog.all.ttl> ] ." +
//			"<http://mydata2> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <file:///home/raven/public_html/test.hdt> ] ." +
      "}").toString()));

    job FactoryBeanObject, Job.create(ctxModel)
        .setOp(
            cj.fromVar(varName).construct("""
PREFIX eg: <http://www.example.org/>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

CONSTRUCT {
  ?x
    a eg:Transition ;
    owl:annotatedSubject ?st ;
    owl:annotatedProperty ?p ;
    owl:annotatedObject ?ot ;
    eg:datasetId ?DATASET_ID ;
    eg:count ?c .

  ?st ?x ?ot .
  ?ot ?y ?st .
  
  ?y owl:inverseOf ?x .
} {
  { SELECT DISTINCT ?st ?p ?ot (COUNT(*) AS ?c) {
    ?s ?p ?o
    FILTER(!STRSTARTS(STR(?p), 'http://www.w3.org/1999/02/22-rdf-syntax-ns#_'))
    FILTER(?p NOT IN (rdf:type))
    # FILTER(!isLiteral(?o))

    OPTIONAL { ?s a ?stmp }
    OPTIONAL { ?o a ?otmp }
    BIND(IF(BOUND(?stmp), ?stmp, eg:unbound) AS ?st)
    BIND(IF(BOUND(?otmp), ?otmp, eg:unbound) AS ?ot)
    
  } GROUP BY ?st ?p ?ot }

  BIND(MD5(CONCAT(STR(?st), STR(?p), STR(?ot))) AS ?hash)
  BIND(IRI(CONCAT("http://www.example.org/transition-", ?hash)) AS ?x)
  BIND(IRI(CONCAT("http://www.example.org/transition-inverse-", ?hash)) AS ?y)  
}
""").cache().getOp()
        )
        .addJobBinding("DATASET_ID", OpTraversalSelf.create(ctxModel))
    ;
}

