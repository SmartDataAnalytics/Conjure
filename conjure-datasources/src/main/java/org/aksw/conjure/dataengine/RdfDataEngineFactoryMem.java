package org.aksw.conjure.dataengine;

import java.util.Map;

import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jenax.dataaccess.sparql.connection.common.RDFConnectionUtils;
import org.aksw.jenax.dataaccess.sparql.dataengine.RdfDataEngine;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFromDataset;
import org.aksw.jenax.dataaccess.sparql.factory.dataset.connection.DatasetRDFConnectionFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataset.connection.DatasetRDFConnectionFactoryBuilder;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.dataaccess.sparql.factory.engine.update.UpdateEngineFactoryCore;
import org.aksw.jenax.dataaccess.sparql.link.common.RDFLinkWrapperWithWorkerThread;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.modify.UpdateEngineMain;
import org.apache.jena.sparql.modify.request.UpdateVisitor;
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

        UpdateEngineFactoryCore uef = (d, b, c) -> new UpdateEngineMain(d, b,c) {
            @Override
            protected UpdateVisitor prepareWorker() {
                return new UpdateEngineWorkerLoadAsGiven(d, b, c) ;
            }
        };

        DatasetRDFConnectionFactory connector = DatasetRDFConnectionFactoryBuilder.create()
                .setDefaultQueryEngineFactoryProvider()
                .setUpdateEngineFactoryCore(uef)
                // .setDefaultUpdateEngineFactoryProvider()
                .setContext(cxt)
                .build();


        RdfDataEngine result = RdfDataEngineFromDataset.create(DatasetFactory.create(), ds -> {
            RDFConnection raw = connector.connect(ds);
            return RDFConnectionUtils.wrapWithLinkDecorator(raw, RDFLinkWrapperWithWorkerThread::wrap);
        }, null);

        return result;
    }
}
