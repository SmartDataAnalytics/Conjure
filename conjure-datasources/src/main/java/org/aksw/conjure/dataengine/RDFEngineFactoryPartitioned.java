package org.aksw.conjure.dataengine;

import java.io.Closeable;
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
import org.aksw.conjure.datasource.DatasetGraphDelegateWithWorkerThread;
import org.aksw.conjure.datasource.DatasetGraphHashPartitioned;
import org.aksw.conjure.datasource.PropertiesUtils;
import org.aksw.jenax.arq.util.dataset.HasDataset;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngine;
import org.aksw.jenax.dataaccess.sparql.engine.RDFEngines;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactoryLegacyBase;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecTerms;
import org.aksw.jenax.dataaccess.sparql.linksource.RDFLinkSource;
import org.aksw.jenax.dataaccess.sparql.linksource.RDFLinkSourceOverDatasetGraph;
import org.apache.jena.rdflink.RDFLink;
import org.apache.jena.sparql.core.DatasetGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class RDFEngineFactoryPartitioned
    extends RDFEngineFactoryLegacyBase
{
    private static final Logger logger = LoggerFactory.getLogger(RDFEngineFactoryPartitioned.class);

    @Override
    public RDFEngine create(Map<String, Object> config) throws Exception {
        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        Entry<Path, Closeable> fsInfo = PathUtils.resolveFsAndPath(spec.getLocationContext(), spec.getLocation());
        Path path = fsInfo.getKey();


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


                 if (!props.containsKey(RdfDataSourceSpecTerms.PARTITIONS)) {
                     props.setProperty(RdfDataSourceSpecTerms.PARTITIONS, Integer.toString(Runtime.getRuntime().availableProcessors()));
                 }

                 Files.createDirectories(path);
                 PropertiesUtils.write(confFile, props);
            }
        }

        String delegateEngine = Objects.requireNonNull((String)props.get(RdfDataSourceSpecTerms.DELEGATE),
                "No delegate engine set which to use for partitioning");


        int numPartitions = Integer.parseInt(
                Objects.requireNonNull(props.getProperty(RdfDataSourceSpecTerms.PARTITIONS), "Number of partitions not specified"));

        RDFEngineFactory delegateFactory = new RDFEngineFactoryRailed(); // RdfDataSourceFactoryRegistry.get().getFactory(delegateEngine);

        List<RDFEngine> partitions = new ArrayList<>();
        FinallyRunAll closePartAction = FinallyRunAll.create();

        for (int i = 0; i < numPartitions; ++i) {
            Properties partProps = (Properties)props.clone();
            Path partLoc = path.resolve("part-" + i);

            partProps.put(RdfDataSourceSpecTerms.LOCATION_KEY, partLoc.toString());

            Map<String, Object> partMap = Maps.transformValues(Maps.fromProperties(partProps), v -> (Object)v);
            RDFEngine engine = delegateFactory.create(partMap);

            closePartAction.addThrowing(engine::close);

            if (engine.getLinkSource().getDatasetGraph() == null) {
                throw new RuntimeException("Partitioning currently requires backing engines to be backed by datasets");
            }

            partitions.add(engine);
        }

        List<DatasetGraph> dsgs = partitions.stream().map(x -> ((HasDataset)x).getDataset().asDatasetGraph())
                .collect(Collectors.toList());


        DatasetGraph ds = DatasetGraphHashPartitioned.createBySubject(dsgs);


        // Set up a datasource for which connections are created in a peculiar way:
        // Each partition member graph gets a wrapper such that access via the connection
        // always uses the same thread
        RDFLinkSource linkSource = new RDFLinkSourceOverDatasetGraph(ds) {
            @Override
            public RDFLink newLink() {
                List<DatasetGraph> guardedDsgs = dsgs.stream()
                        .map(DatasetGraphDelegateWithWorkerThread::wrap)
                        .collect(Collectors.toList());

                // FIXME It looks like closing the link does not close the underyling links.
                DatasetGraph xds = DatasetGraphHashPartitioned.createBySubject(guardedDsgs);
                return RDFLink.connect(xds);
            }
        };

        RDFEngine result = RDFEngines.of(linkSource, ds::close);
        return result;
    }
}

//Path dbPath = fsInfo == null ? null : fsInfo.getKey();
//Closeable fsCloseAction = fsInfo == null ? () -> {} : fsInfo.getValue();
//x -> {
//closePartAction.run();
//});
//

