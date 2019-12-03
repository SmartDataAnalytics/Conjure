package net.sansa_stack.query.spark.conjure;

import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;

public interface ConjureProcessor {
	ConjureResult process(TaskContext taskContext);
}
