package org.aksw.conjure.dataengine;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;

import org.aksw.commons.io.util.PathUtils;
import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryVfsUtils;
import org.aksw.jenax.arq.util.graph.StageGeneratorGraphFindRaw;
import org.aksw.jenax.dataaccess.sparql.dataengine.RdfDataEngine;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFromDataset;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdfDataEngineFactoryBinSearch
    implements RdfDataEngineFactory
{
    private static final Logger logger = LoggerFactory.getLogger(RdfDataEngineFactoryDifs.class);

    @Override
    public RdfDataEngine create(Map<String, Object> config) throws Exception {

        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);

        if (spec.getLocation() == null) {
            throw new IllegalArgumentException("Binary search engine requires a path or URL to a sorted n-triples file.");
        }

        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());
        Path confFile = fsInfo.getKey();

        // boolean canWrite = confFile.getFileSystem().equals(FileSystems.getDefault());

        Context cxt = ARQ.getContext().copy();
        ServiceExecutorFactoryRegistratorVfs.register(cxt);

        Graph graph = ServiceExecutorFactoryVfsUtils.createGraphBinSearch(confFile, cxt);
        Dataset ds = DatasetFactory.wrap(DatasetGraphFactory.wrap(graph));

        ds.getContext().set(ARQ.stageGenerator, new StageGeneratorGraphFindRaw());
        RdfDataEngine result = RdfDataEngineFromDataset.create(ds, true);
        return result;
    }
}
