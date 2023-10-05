package org.aksw.conjure.datasource;

import java.util.Map;

import org.aksw.jenax.dataaccess.sparql.datasource.RdfDataSource;
import org.aksw.jenax.dataaccess.sparql.datasource.RdfDataSourceDelegateBase;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceDecorator;
import org.aksw.jenax.dataaccess.sparql.link.common.RDFLinkDelegateWithWorkerThread;
import org.aksw.jenax.dataaccess.sparql.link.common.RDFLinkUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdflink.LinkDatasetGraph;
import org.apache.jena.rdflink.RDFConnectionAdapter;
import org.apache.jena.rdflink.RDFLink;
import org.apache.jena.rdflink.RDFLinkAdapter;
import org.apache.jena.rdflink.RDFLinkModular;

import net.sansa_stack.spark.io.rdf.loader.LinkDatasetGraphSansa;

public class RdfDataSourceDecoratorSansa
    implements RdfDataSourceDecorator
{
    public static Configuration createDefaultHadoopConfiguration() {
        Configuration conf = new Configuration(false);
        conf.set("fs.defaultFS", "file:///");
        return conf;
    }

    @Override
    public RdfDataSource decorate(RdfDataSource dataSource, Map<String, Object> config) {
        // RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);

        RdfDataSource result = new RdfDataSourceDelegateBase(dataSource) {
            @Override
            public org.apache.jena.rdfconnection.RDFConnection getConnection() {
                RDFConnection rawConn = dataSource.getConnection();
                // RDFLink queryLink = RDFLinkAdapterEx.adapt(rawConn);


                // RDFConnection conn = RDFConnectionAdapter.adapt(RDFLinkDelegateWithWorkerThread.wrap(RDFLinkAdapterEx.adapt(connx)));

                // If true then the graphstore LOAD action may acquire multiple update connections for the INSERT requests
                // Multiple concurrent update transaction are prone to deadlocks

                RDFLink rawUpdateLink = RDFLinkAdapter.adapt(rawConn); // RDFLinkAdapterFix.adapt(rawConn);

                // The underlying engines should enforce same thread on link
                // Note: The input dataset should take care of wrapping with 'DatasetGraphDelegateWithWorkerThread'
                boolean enforceSameThreadOnLink = false;
                RDFLink updateLink = enforceSameThreadOnLink
                        ? RDFLinkDelegateWithWorkerThread.wrap(rawUpdateLink)
                        : rawUpdateLink;


                boolean allowMultipleConnections = false;

                LinkDatasetGraph linkDg;
                if (allowMultipleConnections) {
                    linkDg = LinkDatasetGraphSansa.create(createDefaultHadoopConfiguration(), () -> RDFLinkAdapter.adapt(dataSource.getConnection()));
                } else {
                    linkDg = LinkDatasetGraphSansa.create(createDefaultHadoopConfiguration(), () -> new RDFLinkAdapter(RDFConnectionAdapter.adapt(updateLink)) {
                        @Override
                        public void close() {
                            // noop as we reuse the primary connection - the primary one has to be closed
                        }
                    });
                }

                RDFConnection r = RDFConnectionAdapter.adapt(
                        RDFLinkUtils.wrapWithLoadViaLinkDatasetGraph(new RDFLinkModular(updateLink, updateLink, linkDg)));
                return r;
            }
        };

        return result;
    }

}
