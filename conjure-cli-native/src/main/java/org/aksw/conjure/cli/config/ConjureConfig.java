package org.aksw.conjure.cli.config;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

public class ConjureConfig {
	// TODO We could create a hash code from the config and use it as the temp directory name
	
	protected Set<String> sources;

	/**
	 * A set of (absolute) paths to spring content files (.xml, groovy)
	 * together with their assigned content as a byte array.
	 * 
	 */
	protected Map<String, byte[]> sourceToContent;
		
	public ConjureConfig(Set<String> sources, Map<String, byte[]> sourceToContent) {
		super();
		this.sources = sources;
		this.sourceToContent = sourceToContent;
	}

	public Set<String> getSources() {
		return sources;
	}
	
	public Map<String, byte[]> getSourceToContent() {
		return sourceToContent;
	}
	
	public static Set<String> effectiveSources(Set<String> sources, Map<String, ?> fileSources) {
        Set<String> remappedSources = fileSources.values().stream()
        		.map(Object::toString)
        		.collect(Collectors.toSet());
        
		Set<String> result = Sets.union(
            Sets.difference(sources, fileSources.keySet()),
            remappedSources);

		return result;
	}
}
