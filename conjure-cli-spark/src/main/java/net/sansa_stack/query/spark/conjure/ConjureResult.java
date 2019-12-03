package net.sansa_stack.query.spark.conjure;

import org.apache.jena.rdf.model.Resource;

public class ConjureResult {
	protected Resource dcatRecord;
	protected boolean success;
	protected String message;
	
	public ConjureResult(Resource dcatRecord, boolean success, String message) {
		super();
		this.dcatRecord = dcatRecord;
		this.success = success;
		this.message = message;
	}

	public Resource getDcatRecord() {
		return dcatRecord;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}
}
