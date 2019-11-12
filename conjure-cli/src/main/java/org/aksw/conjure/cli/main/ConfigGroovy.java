package org.aksw.conjure.cli.main;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Configuration;

//@Configuration
//public class ConfigGroovy {
//	//@Override
//	@Bean
//	public BeanDefinitionReader createBeanDefinitionReader(
//			GenericApplicationContext context) {
//		GroovyBeanDefinitionReader result = new GroovyBeanDefinitionReader(context);
//		result.loadBeanDefinitions("conjure-test.groovy");
//		return result;
//	}
//}

@Configuration
public class ConfigGroovy implements BeanDefinitionRegistryPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ConfigGroovy.class);

    private final String config = "conjure-test.groovy";
//
//    public ConfigGroovy(String config) {
//        this.config = config;
//    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        log.info("Loading Groovy config '{}'", config);

        GroovyBeanDefinitionReader reader = new GroovyBeanDefinitionReader(registry);
        try {
            reader.importBeans(config);
        } catch (IOException e) {
            throw new ApplicationContextException("Can't open Groovy config '" + config + "'");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }
}
