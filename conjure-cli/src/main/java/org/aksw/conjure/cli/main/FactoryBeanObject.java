package org.aksw.conjure.cli.main;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public class FactoryBeanObject<T>
	extends AbstractFactoryBean<T>
{
	protected T obj;
	protected Class<?> cls;
	
	public FactoryBeanObject(T obj) {
		super();
		this.obj = obj;
		this.cls = obj.getClass();
	}

	@Override
	public Class<?> getObjectType() {
		return cls;
	}

	@Override
	protected T createInstance() throws Exception {
		return obj;
	}
}
