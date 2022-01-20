package org.aksw.conjure.datasource;

import org.aksw.jenax.arq.datasource.RdfDataSourceFactoryRegistry;
import org.aksw.jenax.arq.datasource.RdfDataSourceFactoryRemote;
import org.apache.jena.sys.JenaSubsystemLifecycle;

public class JenaPluginConjureDataSources
    implements JenaSubsystemLifecycle
{
    public void start() {
        init();
    }

    @Override
    public void stop() {
    }


    public static void init() {
        addDefaults(RdfDataSourceFactoryRegistry.get());
    }

    public static RdfDataSourceFactoryRegistry addDefaults(RdfDataSourceFactoryRegistry registry) {

        synchronized (JenaPluginConjureDataSources.class) {
            registry.putFactory("mem", new RdfDataSourceFactoryMem());
            registry.putFactory("tdb2", new RdfDataSourceFactoryTdb2());
            registry.putFactory("remote", new RdfDataSourceFactoryRemote());
            registry.putFactory("difs", new RdfDataSourceFactoryDifs());
            registry.putFactory("partitioned", new RdfDataSourceFactoryPartitioned());
        }

        return registry;
    }
}
