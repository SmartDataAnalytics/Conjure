package org.aksw.conjure.dataengine;

import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RDFEngineFactoryRegistry;
import org.aksw.jenax.dataaccess.sparql.factory.datasource.RdfDataSourceFactoryRemote;
import org.apache.jena.sys.JenaSubsystemLifecycle;

public class JenaPluginConjureDataEngines
    implements JenaSubsystemLifecycle
{
    public void start() {
        init();
    }

    @Override
    public void stop() {
    }


    public static void init() {
        addDefaults(RDFEngineFactoryRegistry.get());
    }

    public static RDFEngineFactoryRegistry addDefaults(RDFEngineFactoryRegistry registry) {
        synchronized (JenaPluginConjureDataEngines.class) {
            registry.putFactory("mem", new RDFEngineFactoryMem());
            registry.putFactory("tdb2", new RDFEngineFactoryTDB2());
            registry.putFactory("remote", RDFEngineFactory.wrap(new RdfDataSourceFactoryRemote()));
            registry.putFactory("difs", new RDFEngineFactoryDifs());
            registry.putFactory("binsearch", new RDFEngineFactoryBinSearch());
            registry.putFactory("partitioned", new RDFEngineFactoryPartitioned());
        }
        return registry;
    }
}
