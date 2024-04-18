package org.aksw.conjure.dataengine;

import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactory;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngineFactoryRegistry;
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
        addDefaults(RdfDataEngineFactoryRegistry.get());
    }

    public static RdfDataEngineFactoryRegistry addDefaults(RdfDataEngineFactoryRegistry registry) {
        synchronized (JenaPluginConjureDataEngines.class) {
            registry.putFactory("mem", new RdfDataEngineFactoryMem());
            registry.putFactory("tdb2", new RdfDataEngineFactoryTdb2());
            registry.putFactory("remote", RdfDataEngineFactory.wrap(new RdfDataSourceFactoryRemote()));
            registry.putFactory("difs", new RdfDataEngineFactoryDifs());
            registry.putFactory("partitioned", new RdfDataEngineFactoryPartitioned());
        }
        return registry;
    }
}
