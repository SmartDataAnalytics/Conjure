package org.aksw.conjure.cli.config;

public class ConjureConfig {
	//protected SourcesConfig sourcesConfig;

	protected String sparkMaster;
	
	public ConjureConfig() {
		super();
	}

	public String getSparkMaster() {
		return sparkMaster;
	}

	public void setSparkMaster(String sparkMaster) {
		this.sparkMaster = sparkMaster;
	}

//	public SourcesConfig getSourcesConfig() {
//		return sourcesConfig;
//	}
//
//	public void setSourcesConfig(SourcesConfig sourcesConfig) {
//		this.sourcesConfig = sourcesConfig;
//	}
	
	
}
