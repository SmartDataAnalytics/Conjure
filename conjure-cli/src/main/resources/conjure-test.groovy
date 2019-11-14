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

beans {
	dataref FactoryBeanObject, DataRefOp.create(
		OpUpdateRequest.create(model, OpData.create(model),
	    parser.apply("INSERT DATA { <http://mydata> dataid:group eg:mygrp ; dcat:distribution [ dcat:downloadURL <http://localhost/~raven/009e80050fa7f4279596956477157ec2.hdt> ] }").toString()));

	job FactoryBeanObject, Job.create(ctxModel)
		.setOp(cj.coalesce(
			cj.fromUrl(url).hdtHeader().construct("CONSTRUCT WHERE { ?s <urn:tripleCount> ?o }"),
			cj.fromUrl(url).tripleCount().cache()).getOp())
		.addJobBinding("datasetId", OpTraversalSelf.create(ctxModel))
		;
}

