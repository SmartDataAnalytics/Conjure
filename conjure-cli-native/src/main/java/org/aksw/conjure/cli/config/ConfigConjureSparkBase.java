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
    public ConjureConfig conjureMasterConfig(ConjureCliArgs args) 
    {
		ConjureConfig result = parseArgs(args);
		return result;
    }

	public static ConjureConfig parseArgs(ConjureCliArgs args) {
		
		CommandMain cm = args.getCm();
		
	    List<String> rawSources = cm.nonOptionArgs;
	    Set<String> canonicalSources = rawSources.stream()
	    		.map(MainCliConjureNative::canonicalizeSource)
	    		.collect(Collectors.toSet());

	    Path basePath = Paths.get(StandardSystemProperty.USER_DIR.value());

//	    List<String> canonicalSources = rawSources.stream()
//	    		.map(src -> MainCliConjureNative.resolvePath(basePath, src))
//	    		.collect(Collectors.toList());
	    
	    Map<String, byte[]> sourceToContent = MainCliConjureNative.loadSources(basePath, canonicalSources);
	    ConjureConfig result = new ConjureConfig(canonicalSources, sourceToContent);
	    return result;
    }
}
