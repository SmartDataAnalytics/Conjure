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
			"<http://mydata> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <file:///home/raven/public_html/test.hdt> ] ." + 
			"<http://mydata2> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <file:///home/raven/public_html/test.hdt> ] ." +
      "}").toString()));

    job FactoryBeanObject, Job.create(ctxModel)
        .setOp(
          cj.union(
            cj.fromVar(varName).construct("CONSTRUCT { ?DATASET_ID void:property ?p } { { SELECT DISTINCT ?p { ?s ?p ?o } } }").cache(),
            cj.fromVar(varName).construct("CONSTRUCT { ?DATASET_ID void:class ?c } { { SELECT DISTINCT ?c { ?s a ?c } } }").cache()
        )
        .getOp())
        .addJobBinding("DATASET_ID", OpTraversalSelf.create(ctxModel))
    ;
}

