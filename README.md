# Conjure
A declarative approach to conjure RDF datasets from RDF datasets using SPARQL with caching of repeated operations.


## Building dist packages
The spark bundle suitable for spark-submit and its wrapping as a debian package can be built with

`mvn -P dist package`

The spark debian package places a runnable jar-with-dependencies file under `/usr/share/lib/conjure-spark-cli`


## Spring boot

Custom contexts can be specified via these jvm options:
-Dspring.main.sources=ctx1.groovy,ctx2.groovy


