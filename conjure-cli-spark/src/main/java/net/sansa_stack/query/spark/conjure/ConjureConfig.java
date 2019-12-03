package net.sansa_stack.query.spark.conjure;

import java.nio.file.Path;
import java.util.Map;

public class ConjureConfig {
	/**
	 * A set of (absolute) paths to spring content files (.xml, groovy)
	 * together with their assigned content as a byte array.
	 * 
	 */
	protected Map<Path, byte[]> sourcePathToContent;

	public ConjureConfig(Map<Path, byte[]> sourcePathToContent) {
		super();
		this.sourcePathToContent = sourcePathToContent;
	}

	public Map<Path, byte[]> getSourcePathToContent() {
		return sourcePathToContent;
	}
}
