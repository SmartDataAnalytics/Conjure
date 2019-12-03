package net.sansa_stack.query.spark.conjure.kryo;

import org.aksw.jena_sparql_api.conjure.dataset.engine.TaskContext;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.riot.RDFFormat;
import org.apache.spark.serializer.KryoRegistrator;

import com.esotericsoftware.kryo.Kryo;
import com.google.gson.Gson;

// TODO Merge into JenaKryoRegistrator in sansa-rdf
public class KryoRegistratorRDFNode
	implements KryoRegistrator
{

	@Override
	public void registerClasses(Kryo kryo) {
		Gson gson = new Gson();
		//kryo.register(org.apache.jena.rdf.model.RDFNode.class, new RDFNodeSerializer<>(Function.identity(), gson));
		//kryo.register(org.apache.jena.rdf.model.Resource.class, new RDFNodeSerializer<>(RDFNode::asResource, gson));
		//kryo.register(org.apache.jena.rdf.model.impl.R.class, new RDFNodeSerializer<>(RDFNode::asResource, gson));
		kryo.register(ResourceImpl.class, new SerializerRDFNode<>(RDFNode::asResource, gson));
		kryo.register(ModelCom.class, new SerializerModel(RDFFormat.TURTLE_BLOCKS));

		kryo.register(TaskContext.class, new SerializerTaskContext());

		//kryo.register(DataRefOp.class, new SerializerRDFNode<>(RDFNode::asResource, gson));
	}

}
