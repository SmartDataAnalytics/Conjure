package org.aksw.conjure.datasource;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.aksw.commons.io.util.PathUtils;
import org.aksw.commons.util.exception.FinallyRunAll;
import org.aksw.jenax.arq.datasource.HasDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceFactory;
import org.aksw.jenax.arq.datasource.RdfDataSourceFactoryRegistry;
import org.aksw.jenax.arq.datasource.RdfDataSourceFromDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecTerms;
import org.aksw.jenax.connection.datasource.RdfDataSource;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class RdfDataSourceFactoryPartitioned
    implements RdfDataSourceFactory
{
    private static final Logger logger = LoggerFactory.getLogger(RdfDataSourceFactoryPartitioned.class);


    @Override
    public RdfDataSource create(Map<String, Object> config) throws Exception {
        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());
        Path path = fsInfo.getKey();


        String numPartitionsStr = (String)config.get(RdfDataSourceSpecTerms.PARTITIONS);
        Integer numPartitions =  numPartitionsStr == null ? Runtime.getRuntime().availableProcessors() : Integer.parseInt(numPartitionsStr);


        // Check for an existing partitioned db at the given path
        Properties props = new Properties();
        if (path != null) {

            Path confFile = path.resolve("rpt-partition.properties");
            if (Files.exists(confFile)) {
                PropertiesUtils.read(confFile, props);
            } else {
                 if (Files.exists(path)) {
                    boolean isEmptyDir = Files.list(path).anyMatch(x -> true);

                    if (!isEmptyDir) {
                        throw new RuntimeException("Creation of a partitioned store requires an empty directory");
                    }
                 }

                 props.putAll(config);
                 props.put(RdfDataSourceSpecTerms.PARTITIONS, Integer.toString(numPartitions));

                 Files.createDirectories(path);
                 PropertiesUtils.write(confFile, props);
            }
        }

        String delegateEngine = Objects.requireNonNull((String)config.get(RdfDataSourceSpecTerms.DELEGATE),
                "No delegate engine set which to use for partitioning");

        RdfDataSourceFactory delegateFactory = new RdfDataSourceFactoryRailed(); // RdfDataSourceFactoryRegistry.get().getFactory(delegateEngine);

        List<RdfDataSource> partitions = new ArrayList<>();
        FinallyRunAll closePartAction = FinallyRunAll.create();

        for (int i = 0; i < numPartitions; ++i) {
            Properties partProps = (Properties)props.clone();
            Path partLoc = path.resolve("part-" + i);

            partProps.put(RdfDataSourceSpecTerms.LOCATION_KEY, partLoc.toString());

            Map<String, Object> partMap = Maps.transformValues(Maps.fromProperties(partProps), v -> (Object)v);
            RdfDataSource dataSource = delegateFactory.create(partMap);

            closePartAction.addThrowing(dataSource::close);

            if (!(dataSource instanceof HasDataset)) {
                throw new RuntimeException("Partitioning currently requires backing engines to be backed by datasets");
            }

            partitions.add(dataSource);


        }

        List<DatasetGraph> dsgs = partitions.stream().map(x -> ((HasDataset)x).getDataset().asDatasetGraph())
                .collect(Collectors.toList());


        Dataset ds = DatasetFactory.wrap(DatasetGraphHashPartitioned.createBySubject(dsgs));


        // Set up a datasource for which connections are created in a peculiar way:
        // Each partition member graph gets a wrapper such that access via the connection
        // always uses the same thread
        RdfDataSource result = RdfDataSourceFromDataset.create(ds, dummyDs -> {
            List<DatasetGraph> guardedDsgs = dsgs.stream()
                    .map(DatasetGraphDelegateWithWorkerThread::wrap)
                    .collect(Collectors.toList());

            Dataset xds = DatasetFactory.wrap(DatasetGraphHashPartitioned.createBySubject(guardedDsgs));
            return RDFConnection.connect(xds);
        }, () -> ds.close());

//        x -> {
//            closePartAction.run();
//        });
//

        return result;

//        Path dbPath = fsInfo == null ? null : fsInfo.getKey();
//        Closeable fsCloseAction = fsInfo == null ? () -> {} : fsInfo.getValue();
    }
}