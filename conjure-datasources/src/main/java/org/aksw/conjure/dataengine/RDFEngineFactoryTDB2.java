package org.aksw.conjure.dataengine;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aksw.jenax.dataaccess.sparql.creator.FileSet;
import org.aksw.jenax.dataaccess.sparql.creator.FileSetMatcher;
import org.aksw.jenax.dataaccess.sparql.creator.FileSetOverPathMatcher;
import org.aksw.jenax.dataaccess.sparql.creator.FileSets;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngine;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngines;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactoryLegacyBase;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.dataaccess.sparql.link.transform.RDFLinkTransforms;
import org.aksw.jenax.dataaccess.sparql.linksource.RDFLinkSource;
import org.aksw.jenax.dataaccess.sparql.linksource.RDFLinkSourceOverDatasetGraph;
import org.apache.jena.dboe.base.file.Location;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RDFEngineFactoryTDB2
    extends RDFEngineFactoryLegacyBase
{
    private static final Logger logger = LoggerFactory.getLogger(RDFEngineFactoryTDB2.class);

    public class FileSetMatcherTdb2
        implements FileSetMatcher
    {
        @Override
        public List<Path> match(Path basePath) throws IOException {
            List<Path> dataFolders = new ArrayList<>();
            FileSets.accumulate(dataFolders, basePath, "Data*");
            List<Path> result = new ArrayList<>();
            FileSets.accumulateIfExists(result, basePath.resolve("tdb.lock"));
            for (Path dataFolder : dataFolders) {
                FileSets.listPathsDepthFirst(result, dataFolder);
            }
            return result;
        }
    }

    @Override
    public RDFEngine create(Map<String, Object> config) throws Exception {
        RDFEngine result;

        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        CloseablePath entry = RDFEngineFactoryLegacyBase.setupPath("rpt-tdb2-", spec);
        Path finalDbPath = entry.path();
        Closeable partialCloseAction = entry.closeable();

        // boolean deleteOnClose = entry.deleteOnClose();
        boolean autoDeleteRequested = Boolean.TRUE.equals(spec.isAutoDeleteIfCreated());

        FileSet tdbFileSet = new FileSetOverPathMatcher(finalDbPath, new FileSetMatcherTdb2());
        boolean deleteOnClose = autoDeleteRequested && tdbFileSet.isEmpty();

        Location location = Location.create(finalDbPath);
        try {
            DatasetGraph dg = TDB2Factory.connectDataset(location).asDatasetGraph();

//            PathMatcher fileMatcher = path -> {
//                String fileName = path.getFileName().toString().toLowerCase();
//                return fileName.contains("spo").
//            };

            // Dataset dataset = DatasetFactory.wrap(new DatasetGraphWrapperWithSize(dg, finalDbPath, null));

            if (logger.isInfoEnabled()) {
                logger.info("Connecting to TDB2 database in folder " + finalDbPath);
            }
            Closeable finalDeleteAction = () -> {
                try {
                    dg.close();

                    if (deleteOnClose) {
                        logger.info("Attempting to delete TDB2 folder");
                        tdbFileSet.delete();
                        // MoreFiles.deleteRecursively(finalDbPath);
                    }
                } finally {
                    partialCloseAction.close();
                }
            };

            RDFLinkSource linkSource = new RDFLinkSourceOverDatasetGraph(dg);
            result = RDFEngines.of(linkSource, finalDeleteAction);

            // Requests first have to go through the worker thread so that
            // automatically started transactions run on the right thread
//            result = RdfDataEngines.wrapWithAutoTxn(result, dataset);
//            result = RdfDataEngines.wrapWithWorkerThread(result);

            result = RDFEngines.decorate(result)
                .decorate(RDFLinkTransforms.withAutoTxn())
                .decorate(RDFLinkTransforms.withWorkerThread())
                .build();

            // Make sure to expose the underlying dataset
//            if (!(result instanceof RdfDataEngineWithDataset)) {
//                RDFEngine tmp = result;
//
//                result = new RdfDataEngineWithDataset() {
//                    @Override
//                    public Dataset getDataset() {
//                        return dataset;
//                    }
//
//                    @Override
//                    public void close() throws Exception {
//                        tmp.close();
//                    }
//
//                    @Override
//                    public RDFConnection getConnection() {
//                        return tmp.getConnection();
//                    }
//                };
//            }
        } catch (Exception e) {
            partialCloseAction.close();
            throw new RuntimeException(e);
        }

        return result;
    }
}
