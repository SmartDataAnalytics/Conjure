package org.aksw.conjure.cli.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.aksw.conjure.cli.main.CommandMain;
import org.aksw.conjure.cli.main.MainCliConjureNative;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.StandardSystemProperty;

/**
 * Configuration class which creates beans related to
 * parsing the conjure command line arguments 
 * eventually into a ConjureConfig bean
 * 
 * @author raven
 *
 */
@Configuration
public class ConfigConjureSparkBase {

	@Bean
	@Autowired
    public ConjureCliArgs conjureMasterCliArgs(ApplicationArguments args) 
    {
		ConjureCliArgs result = ConjureCliArgs.parse(args.getSourceArgs());
		return result;
    }
	
	@Bean
	@Autowired
    public SpringSourcesConfig conjureMasterConfig(ConjureCliArgs args) 
    {
		SpringSourcesConfig result = parseArgs(args);
		return result;
    }

	public static SpringSourcesConfig parseArgs(ConjureCliArgs args) {

		Path basePath = Paths.get(StandardSystemProperty.USER_DIR.value());
		
		CommandMain cm = args.getCm();
		
	    List<String> rawSources = cm.nonOptionArgs;
	    Set<String> canonicalSources = rawSources.stream()
	    		.map(rawSource -> MainCliConjureNative.canonicalizeSource(basePath, rawSource))
	    		.collect(Collectors.toSet());

//	    List<String> canonicalSources = rawSources.stream()
//	    		.map(src -> MainCliConjureNative.resolvePath(basePath, src))
//	    		.collect(Collectors.toList());
	    
	    Map<String, byte[]> sourceToContent = MainCliConjureNative.loadSources(basePath, canonicalSources);
	    SpringSourcesConfig result = new SpringSourcesConfig(canonicalSources, sourceToContent);
	    
	    
	    
	    return result;
    }
}
