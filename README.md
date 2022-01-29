# Conjure
A declarative approach to conjure RDF datasets from RDF datasets using SPARQL with caching of repeated operations.
Conjure enables blazing fast data profiling and analysis on the basis of RDF dataset catalogs.

Conjure comprises tools for

* detecting content types and encodings
* conversions between content types and encondings
* an RDF vocabulary to model data access (DataRef classes, e.g. DataRefUrl, DataRefSparqlEndpoint)
* an RDF vocabulary to model sparql-based data transformation workflows (Op classes, e.g. OpUnion, OpCoalesce)
* providing implementations of these vocabularies access d data storge (git, webdav)

Conjure builds on several existing libraries including Apache tika, Jena, jgit and more.


## Concept
* First, one provides a specification of an abstract collection of input records (RDF resources) which carry references to (RDF) datasets. The use of DCAT dataset specifications is the recommended and natively supported approach.
* Finally, one provides a job specification whose execution maps each of the input records to an output dataset.
* The output is a data catalog with provenance information referring to the result dataset files.


```
dataSources = dataSourceAssembler.assemble()
dataSources.map(processor)

```

The processor is a mere Spring Bean. Thus, the *exact same conjure configuration* can be used
in arbitrary environments, regardless of single, multi-threaded or cluster setups such as using native Java or Apache Spark.





## Building dist packages
The spark bundle suitable for spark-submit and its wrapping as a debian package can be built with

`mvn -P dist package`

The spark debian package places a runnable jar-with-dependencies file under `/usr/share/lib/conjure-spark-cli`


## Spring boot

Custom contexts can be specified via these jvm options:
-Dspring.main.sources=ctx1.groovy,ctx2.groovy


