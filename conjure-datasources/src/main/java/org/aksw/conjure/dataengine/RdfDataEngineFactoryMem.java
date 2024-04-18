package org.aksw.conjure.dataengine;

import java.util.Iterator;
import java.util.Map;

import org.aksw.jena_sparql_api.arq.service.vfs.ServiceExecutorFactoryRegistratorVfs;
import org.aksw.jenax.dataaccess.sparql.connection.common.RDFConnectionUtils;
import org.aksw.jenax.dataaccess.sparql.dataengine.RdfDataEngine;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFromDataset;
import org.aksw.jenax.dataaccess.sparql.factory.dataset.connection.DatasetRDFConnectionFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataset.connection.DatasetRDFConnectionFactoryBuilder;
import org.aksw.jenax.dataaccess.sparql.factory.dataset.connection.QueryExecDatasetBuilderEx;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasic;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceSpecBasicFromMap;
import org.aksw.jenax.dataaccess.sparql.factory.engine.query.QueryEngineFactoryProvider;
import org.aksw.jenax.dataaccess.sparql.factory.engine.update.UpdateEngineFactoryCore;
import org.aksw.jenax.dataaccess.sparql.link.common.RDFLinkWrapperWithWorkerThread;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.engine.QueryEngineRegistry;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingRoot;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.QueryExecBuilder;
import org.apache.jena.sparql.modify.UpdateEngineMain;
import org.apache.jena.sparql.modify.request.UpdateVisitor;
import org.apache.jena.sparql.syntax.Element;
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

        // Enable virtual file system extension
        // FIXME Move this elsewhere
        ServiceExecutorFactoryRegistratorVfs.register(cxt);

        QueryEngineFactoryProvider queryEngineFactoryProvider = QueryEngineRegistry::findFactory;
        UpdateEngineFactoryCore uef = (d, b, c) -> new UpdateEngineMain(d, b, c) {
            @Override
            protected UpdateVisitor prepareWorker() {
                return new UpdateEngineWorkerLoadAsGiven(d, b, c) {
                    @Override
                    protected Iterator<Binding> evalBindings(Element pattern) {
                        Query query = elementToQuery(pattern);
                        // The UpdateProcessorBase already copied the context and made it safe
                        // ... but that's going to happen again :-(
                        if (query == null) {
                            Binding binding = (null != inputBinding) ? inputBinding : BindingRoot.create();
                            return Iter.singleton(binding);
                        }

                        // Not QueryExecDataset.dataset(...) because of initialBinding.

                        QueryExecBuilder builder = new QueryExecDatasetBuilderEx<>(d, queryEngineFactoryProvider).context(context).query(query);
                        if (inputBinding != null) {
//                            // Must use initialBinding - it puts the input in the results, unlike substitution.
                            builder.substitution(inputBinding);
//                            // substitution does not put results in the output.
//                            // builder.substitution(inputBinding);
                        }
                        QueryExec qExec = builder.build();
                        Iterator<Binding> r = Iter.onClose(qExec.select(), qExec::close);
                        return r;
                    }
                };
            }
        };

        DatasetRDFConnectionFactory connector = DatasetRDFConnectionFactoryBuilder.create()
            .setQueryEngineFactoryProvider(queryEngineFactoryProvider)
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
