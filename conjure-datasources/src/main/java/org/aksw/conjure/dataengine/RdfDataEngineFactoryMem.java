package org.aksw.conjure.dataengine;

import java.util.Map;

import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jenax.arq.connection.core.RDFConnectionUtils;
import org.aksw.jenax.arq.connection.dataset.DatasetRDFConnectionFactory;
import org.aksw.jenax.arq.connection.dataset.DatasetRDFConnectionFactoryBuilder;
import org.aksw.jenax.arq.connection.link.RDFLinkDelegateWithWorkerThread;
import org.aksw.jenax.arq.datasource.RdfDataEngineFactory;
import org.aksw.jenax.arq.datasource.RdfDataEngineFromDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.connection.dataengine.RdfDataEngine;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.util.Context;

public class RdfDataEngineFactoryMem
    implements RdfDataEngineFactory
{
    @Override
    public RdfDataEngine create(Map<String, Object> config) {
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


        RdfDataEngine result = RdfDataEngineFromDataset.create(DatasetFactory.create(), ds -> {
            RDFConnection raw = connector.connect(ds);
            return RDFConnectionUtils.wrapWithLinkDecorator(raw, RDFLinkDelegateWithWorkerThread::wrap);
        }, null);

        return result;
    }
}