package org.aksw.conjure.cli.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
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

@Deprecated
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
        int numBeans = reader.loadBeanDefinitions(config);
		System.out.println("Beans loaded: " + numBeans);
        System.out.println("Registry: " + registry);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }
}
