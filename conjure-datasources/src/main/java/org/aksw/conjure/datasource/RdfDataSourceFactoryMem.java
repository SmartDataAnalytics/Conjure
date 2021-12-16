package org.aksw.conjure.datasource;

import java.util.Map;

import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jenax.arq.connection.core.RDFConnectionUtils;
import org.aksw.jenax.arq.connection.dataset.DatasetRDFConnectionFactory;
import org.aksw.jenax.arq.connection.dataset.DatasetRDFConnectionFactoryBuilder;
import org.aksw.jenax.arq.connection.link.RDFLinkDelegateWithWorkerThread;
import org.aksw.jenax.arq.datasource.RdfDataSourceFactory;
import org.aksw.jenax.arq.datasource.RdfDataSourceFromDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.connection.datasource.RdfDataSource;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.util.Context;

public class RdfDataSourceFactoryMem
    implements RdfDataSourceFactory
{
    @Override
    public RdfDataSource create(Map<String, Object> config) {
        RdfDataSourceSpecBasic spec = RdfDataSourceSpecBasicFromMap.wrap(config);
        if (spec.getLocation() != null) {
            throw new IllegalArgumentException("In-Memory data source does not accept a location.");
        }

        Context cxt = ARQ.getContext().copy();
        ServiceExecutorFactoryRegistratorVfs.register(cxt);

        DatasetRDFConnectionFactory connector = DatasetRDFConnectionFactoryBuilder.create()
                .setDefaultQueryEngineFactoryProvider()
                .setDefaultUpdateEngineFactoryProvider()
                .setContext(cxt)
                .build();


        RdfDataSource result = RdfDataSourceFromDataset.create(DatasetFactory.create(), ds -> {
            RDFConnection raw = connector.connect(ds);
            return RDFConnectionUtils.wrapWithLinkDecorator(raw, RDFLinkDelegateWithWorkerThread::wrap);
        }, null);

        return result;
    }
}