import org.aksw.jena_sparql_api.conjure.fluent.ConjureBuilderImpl

url = "http://localhost/~raven/test.hdt"

beans {
	job: new ConjureBuilderImpl().cj.coalesce(
		cj.fromUrl(url).hdtHeader().construct("CONSTRUCT WHERE { ?s <urn:tripleCount> ?o }"),
		cj.fromUrl(url).tripleCount().cache()).getOp();
}
