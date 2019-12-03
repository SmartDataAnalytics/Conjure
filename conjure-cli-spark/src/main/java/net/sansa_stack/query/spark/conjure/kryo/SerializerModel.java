package net.sansa_stack.query.spark.conjure.kryo;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class SerializerModel
	extends Serializer<Model>
{

	protected RDFFormat format;

	public SerializerModel(RDFFormat format) {
		super();
		this.format = format;
	}

	public Model read(Kryo kryo, Input input, Class<Model> clazz) {
		kryo.readClass(input);
		Model result = ModelFactory.createDefaultModel();
		RDFDataMgr.read(result, input, format.getLang());
		return result;
	}

	public void write(Kryo kryo, Output output, Model model) {
		kryo.writeClass(output, Model.class);
		RDFDataMgr.write(output, model, format);
	}

}