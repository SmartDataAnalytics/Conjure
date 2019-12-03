package org.aksw.conjure.cli.config;

import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;

public interface ConjureProcessor {
	ConjureResult process(TaskContext taskContext);
}
