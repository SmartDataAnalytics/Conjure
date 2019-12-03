package net.sansa_stack.query.spark.conjure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aksw.conjure.cli.main.CommandMain;
import org.aksw.conjure.cli.main.MainCliConjureSimple;
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

	    Path basePath = Paths.get(StandardSystemProperty.USER_DIR.value());

	    List<Path> sourcePaths = rawSources.stream()
	    		.map(src -> MainCliConjureSimple.resolvePath(basePath, src))
	    		.collect(Collectors.toList());
	    
	    Map<Path, byte[]> sourcePathToContent = sourcePaths.stream()
	    		.collect(Collectors.toMap(path -> path, path -> {
					try {
						return Files.readAllBytes(path);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}));
	    		
	    ConjureConfig result = new ConjureConfig(sourcePathToContent);
	    return result;
    }
}
