package net.sansa_stack.query.spark.conjure.kryo;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.aksw.jena_sparql_api.conjure.dataset.algebra.Op;
import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;
import org.aksw.jena_sparql_api.mapper.proxy.JenaPluginUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class SerializerTaskContext
	extends Serializer<TaskContext>
{

	@Override
	public void write(Kryo kryo, Output output, TaskContext ctx) {
		kryo.writeClassAndObject(output, ctx.getInputRecord());
		
		Map<String, Resource> tmpDataRefMap = ctx.getDataRefMapping().entrySet().stream()
			.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().asResource()));
		
		kryo.writeClassAndObject(output, tmpDataRefMap);
		kryo.writeClassAndObject(output, ctx.getCtxModels());
	}

	@Override
	public TaskContext read(Kryo kryo, Input input, Class<TaskContext> type) {
		Resource inputRecord = (Resource)kryo.readClassAndObject(input);
		@SuppressWarnings("unchecked")
		Map<String, Resource> tmpDataRefMap = (Map<String, Resource>)kryo.readClassAndObject(input);
		Map<String, Op> dataRefMap = tmpDataRefMap.entrySet().stream()
				.collect(Collectors.toMap(Entry::getKey, e -> JenaPluginUtils.polymorphicCast(e.getValue(), Op.class)));

		@SuppressWarnings("unchecked")
		Map<String, Model> cxtModels = (Map<String, Model>)kryo.readClassAndObject(input);

		TaskContext result = new TaskContext(inputRecord, dataRefMap, cxtModels);

		return result;
	}
	
}
