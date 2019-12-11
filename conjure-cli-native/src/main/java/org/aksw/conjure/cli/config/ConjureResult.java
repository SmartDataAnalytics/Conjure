package org.aksw.conjure.cli.config;

import java.util.Map;

import org.apache.jena.rdf.model.Resource;

public class ConjureResult {
	protected Resource dcatRecord;
	protected boolean success;
	protected String message;
	
	// File content directly as part of a result, usefule
	// for returning datasets via spark
	protected Map<String, byte[]> fileMap;
	
	public ConjureResult(Resource dcatRecord, boolean success, String message, Map<String, byte[]> fileMap) {
		super();
		this.dcatRecord = dcatRecord;
		this.success = success;
		this.message = message;
		this.fileMap = fileMap;
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

	public Map<String, byte[]> getFileMap() {
		return fileMap;
	}
}
