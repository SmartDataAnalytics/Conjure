import org.aksw.conjure.cli.main.FactoryBeanObject
import org.aksw.jena_sparql_api.conjure.fluent.ConjureBuilderImpl
import org.aksw.jena_sparql_api.conjure.job.api.Job

// Useful information about bean definitions in groovy:
// https://spring.io/blog/2014/03/03/groovy-bean-configuration-in-spring-framework-4

url = "http://localhost/~raven/test.hdt"
cj = new ConjureBuilderImpl()

beans {
	job FactoryBeanObject, cj.coalesce(
		cj.fromUrl(url).hdtHeader().construct("CONSTRUCT WHERE { ?s <urn:tripleCount> ?o }"),
		cj.fromUrl(url).tripleCount().cache()).getOp();
}
