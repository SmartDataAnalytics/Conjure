package org.aksw.conjure.dataengine;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;

import org.aksw.commons.io.util.PathUtils;
import org.aksw.conjure.datasource.DatasetGraphWrapperWithSize;
import org.aksw.jenax.arq.connection.core.RDFConnectionUtils;
import org.aksw.jenax.arq.connection.link.RDFLinkDelegateWithWorkerThread;
import org.aksw.jenax.arq.datasource.RdfDataEngineFactory;
import org.aksw.jenax.arq.datasource.RdfDataEngineFromDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.connection.dataengine.RdfDataEngine;
import org.apache.jena.dboe.base.file.Location;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.StandardSystemProperty;
import com.google.common.io.MoreFiles;

public class RdfDataEngineFactoryTdb2
    implements RdfDataEngineFactory
{
    private static final Logger logger = LoggerFactory.getLogger(RdfDataEngineFactoryTdb2.class);


    @Override
    public RdfDataEngine create(Map<String, Object> config) throws Exception {
        RdfDataEngine result;

        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());

        Path dbPath = fsInfo == null ? null : fsInfo.getKey();
        Closeable fsCloseAction = fsInfo == null ? () -> {} : fsInfo.getValue();


        boolean createdDbDir = false;

        if (dbPath == null) {
            String tmpDirStr = spec.getTempDir();
            if (tmpDirStr == null) {
                tmpDirStr = StandardSystemProperty.JAVA_IO_TMPDIR.value();
            }

            if (tmpDirStr == null) {
                throw new IllegalStateException("Temp dir neither specified nor obtainable from java.io.tmpdir");
            }

            Path tmpDir = Paths.get(tmpDirStr);
            dbPath = Files.createTempDirectory(tmpDir, "sparql-integrate-tdb2-").toAbsolutePath();
            createdDbDir = true;
        } else {
            dbPath = dbPath.toAbsolutePath();
            if (!Files.exists(dbPath)) {
                Files.createDirectories(dbPath);
                createdDbDir = true;
            }
        }

        Path finalDbPath = dbPath;
        Closeable deleteAction;
        if (createdDbDir) {
            if (Boolean.TRUE.equals(spec.isAutoDeleteIfCreated())) {
                logger.info("Created new directory (its content will deleted when done): " + finalDbPath);
                deleteAction = () -> {
                    logger.info("Deleting created directory: " + finalDbPath);
                    MoreFiles.deleteRecursively(finalDbPath);
                };
            } else {
                logger.info("Created new directory (will be kept after done): " + finalDbPath);
                deleteAction = () -> {};
            }
        } else {
            logger.warn("Folder already existed - delete action disabled: " + finalDbPath);
            deleteAction = () -> {};
        }

        // Set up a partial close action because connecting to the db may yet fail
        Closeable partialCloseAction = () -> {
            try {
                deleteAction.close();
            } finally {
                fsCloseAction.close();
            }
        };

        Location location = Location.create(finalDbPath);
        try {
            DatasetGraph dg = TDB2Factory.connectDataset(location).asDatasetGraph();

//            PathMatcher fileMatcher = path -> {
//                String fileName = path.getFileName().toString().toLowerCase();
//                return fileName.contains("spo").
//            };

            Dataset dataset = DatasetFactory.wrap(new DatasetGraphWrapperWithSize(dg, finalDbPath, null));

            logger.info("Connecting to TDB2 database in folder " + finalDbPath);
            Closeable finalDeleteAction = () -> {
                try {
                    dataset.close();
                } finally {
                    partialCloseAction.close();
                }
            };

            result = RdfDataEngineFromDataset.create(
                    dataset,
                    ds -> {
                        RDFConnection raw = RDFConnection.connect(dataset);
                        return RDFConnectionUtils.wrapWithLinkDecorator(raw, RDFLinkDelegateWithWorkerThread::wrap);
                    },
                    finalDeleteAction);
        } catch (Exception e) {
            partialCloseAction.close();
            throw new RuntimeException(e);
        }

        return result;
    }
}