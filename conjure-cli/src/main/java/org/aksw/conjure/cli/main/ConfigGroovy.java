package org.aksw.conjure.cli.main;

import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

@Configuration
public class ConfigGroovy {
	//@Override
	@Bean
	public BeanDefinitionReader createBeanDefinitionReader(
			GenericApplicationContext context) {
		GroovyBeanDefinitionReader result = new GroovyBeanDefinitionReader(context);
		result.loadBeanDefinitions("conjure-test.groovy");
		return result;
	}
}

//public class ConfigGroovy implements BeanDefinitionRegistryPostProcessor {
//    private static final Logger log = LoggerFactory.getLogger(ConfigGroovy.class);
//
//    private final String config;
//
//    public ConfigGroovy(String config) {
//        this.config = config;
//    }
//
//    @Override
//    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
//        log.info("Loading Groovy config '{}'", config);
//
//        GroovyBeanDefinitionReader reader = new GroovyBeanDefinitionReader(registry);
//        try {
//            reader.importBeans(config);
//        } catch (IOException e) {
//            throw new ApplicationContextException("Can't open Groovy config '" + config + "'");
//        }
//    }
//
//    @Override
//    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//    }
//}
