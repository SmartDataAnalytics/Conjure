package org.aksw.conjure.dataengine;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;

import org.aksw.commons.io.util.PathUtils;
import org.aksw.conjure.datasource.DatasetGraphRailed;
import org.aksw.conjure.datasource.PropertiesUtils;
import org.aksw.jenax.dataaccess.sparql.connection.common.RDFConnectionUtils;
import org.aksw.jenax.dataaccess.sparql.dataengine.RdfDataEngine;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactoryRegistry;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFromDataset;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecTerms;
import org.aksw.jenax.dataaccess.sparql.link.common.RDFLinkWrapperWithWorkerThread;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdfDataEngineFactoryRailed
    implements RdfDataEngineFactory
{
    private static final Logger logger = LoggerFactory.getLogger(RdfDataEngineFactoryRailed.class);


    @Override
    public RdfDataEngine create(Map<String, Object> config) throws Exception {
        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());
        Path path = fsInfo.getKey();


        String railSizeStr = (String)config.get(RdfDataSourceSpecTerms.RAIL_SIZE);
        Long railSize = railSizeStr == null ? null : Long.parseLong(railSizeStr);

        Objects.requireNonNull(railSize, "Rail size must be specified (this should be roughly free ram / num partitions");

        // Check for an existing partitioned db at the given path
        Properties props = new Properties();
        Path confFile;
        if (path != null) {

            confFile = path.resolve("rpt-rail.properties");
            if (Files.exists(confFile)) {
                PropertiesUtils.read(confFile, props);
            } else {
                 if (Files.exists(path)) {
                    boolean isEmptyDir = Files.list(path).anyMatch(x -> true);

                    if (!isEmptyDir) {
                        throw new RuntimeException("Creation of a railed store requires an empty directory");
                    }
                 }

                 props.putAll(config);

                 // props.put(RdfDataSourceSpecTerms.RAIL_SIZE, Integer.toString(numPartitions));

                 Files.createDirectories(path);
                 PropertiesUtils.write(confFile, props);
            }
        } else {
            throw new RuntimeException("No location specified for railed dataset");
        }

        String delegateEngine = Objects.requireNonNull((String)config.get(RdfDataSourceSpecTerms.DELEGATE),
                "No delegate engine set which to use for railing");

        RdfDataEngineFactory delegateFactory = RdfDataEngineFactoryRegistry.get().getFactory(delegateEngine);

        DatasetGraph dg = new DatasetGraphRailed(confFile, delegateFactory);
        Dataset ds = DatasetFactory.wrap(dg);

        RdfDataEngine result = RdfDataEngineFromDataset.create(ds, dss -> {
            RDFConnection raw = RDFConnection.connect(dss);
            return RDFConnectionUtils.wrapWithLinkDecorator(raw, RDFLinkWrapperWithWorkerThread::wrap);
        }, null);

//        x -> {
//            closePartAction.run();
//        });
//

        return result;

//        Path dbPath = fsInfo == null ? null : fsInfo.getKey();
//        Closeable fsCloseAction = fsInfo == null ? () -> {} : fsInfo.getValue();
    }
}
