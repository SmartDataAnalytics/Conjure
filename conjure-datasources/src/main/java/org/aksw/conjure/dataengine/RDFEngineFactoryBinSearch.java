package org.aksw.conjure.dataengine;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

import org.aksw.commons.io.util.PathUtils;
import org.aksw.jena_sparql_api.io.binseach.GraphFindCache;
import org.aksw.jena_sparql_api.io.binseach.StageGeneratorGraphFindRaw;
import org.aksw.jenax.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jenax.arq.service.vfs.ServiceExecutorFactoryVfsUtils;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngine;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngines;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactoryLegacyBase;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.ARQ;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;

public class RDFEngineFactoryBinSearch
    extends RDFEngineFactoryLegacyBase
{
    // private static final Logger logger = LoggerFactory.getLogger(RdfDataEngineFactoryBinSearch.class);

    @Override
    public RDFEngine create(Map<String, Object> config) throws Exception {

        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);

        if (spec.getLocation() == null) {
            throw new IllegalArgumentException("Binary search engine requires a path or URL to a sorted n-triples file.");
        }

        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());
        Path dataFile = fsInfo.getKey();

        if (!Files.exists(dataFile)) {
            throw new FileNotFoundException("File does not exist: " + dataFile.toAbsolutePath());
        }

        Context cxt = ARQ.getContext().copy();
        ServiceExecutorFactoryRegistratorVfs.register(cxt);

        Graph graph = ServiceExecutorFactoryVfsUtils.createGraphBinSearch(dataFile, cxt);
        DatasetGraph ds = DatasetGraphFactory.wrap(graph);

        ds.getContext().set(ARQ.stageGenerator, new StageGeneratorGraphFindRaw());
        ds.getContext().set(GraphFindCache.graphCache, new GraphFindCache(10000));

        RDFEngine result = RDFEngines.of(ds);
        return result;
    }
}
