/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Contribute {@link PropertySource property sources} to the {@link Environment}.
 *
 * <p>This class is stateful and merge descriptors with the same name in a
 * single {@link PropertySource} rather than creating dedicated ones.
 *
 * @author Stephane Nicoll
 * @since 6.0
 * @see PropertySourceDescriptor
 */
public class PropertySourceProcessor {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Log logger = LogFactory.getLog(PropertySourceProcessor.class);

	private final ConfigurableEnvironment environment;

	private final ResourceLoader resourceLoader;

	private final List<String> propertySourceNames;

	public PropertySourceProcessor(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.propertySourceNames = new ArrayList<>();
	}

	/**
	 * Process the specified {@link PropertySourceDescriptor} against the
	 * environment managed by this instance.
	 * @param descriptor the descriptor to process
	 * @throws IOException if loading the properties failed
	 *
	 * 取出descriptor中的属性（name，encoding、locations等等） 加载配置文件的内容,
	 * 并将资源放在环境变量当中
	 */
	public void processPropertySource(PropertySourceDescriptor descriptor) throws IOException {
		String name = descriptor.name();
		String encoding = descriptor.encoding();
		List<String> locations = descriptor.locations();
		Assert.isTrue(locations.size() > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = descriptor.ignoreResourceNotFound();
		PropertySourceFactory factory = (descriptor.propertySourceFactory() != null ?
				instantiateClass(descriptor.propertySourceFactory()) : DEFAULT_PROPERTY_SOURCE_FACTORY);

		for (String location : locations) {
			try {
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				//加载配置文件的内容
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				//把资源放到环境变量里
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	/**
	 * 将获取到的属性值添加到Spring的环境变量的资源容器中
	 * @param propertySource 里面已经有了资源名称name 和 编码过后的 EncodedResource
	 *
	 *  大体流程：
	 *  	1.拿到环境变量里的MutablePropertySources propertySources
	 *      2.查看之前是否已经加载过同名的资源，如果资源存在，需要把当前的资源放在同名资源前面
	 *        最终都是放在CompositePropertySource 的 propertySources当中。
	 *      	2.1 根据传进来的 propertySource 的类型得到一个newSource
	 *          2.2 判断存在的资源是不是 CompositePropertySource 类型，
	 *              	是的话说明当前这个新资源应该加入 CompositePropertySource 的propertySources当中
	 *			2.3 判断存在的资源是不是 ResourcePropertySource 类型，
	 *		            是的话创建一个 CompositePropertySource 类型的资源，并把存在的和新的资源都放进 propertySources 里
	 *		 3.当前没有加载过资源，把传进来的资源放进环境变量的资源里
	 *		 4.加载过资源，但是没有同名的资源，把当前资源放在最后一个资源（第一次添加的资源）前面
	 *		 5.把新加载的资源名称放到propertySourceNames中
	 *
	 *
	 * 	 注意：CompositePropertySource会被作为资源放到MutablePropertySources中的propertySourceList中
	 * 	      我们新传入的资源和已存在的资源都会放到CompositePropertySource.propertySources里。
	 * 	      十分重要：
	 * 	      ******这里的结构是嵌套的 propertySources-->不同名字对应的CompositePropertySource-->相同名字的所有资源*****
	 *
	 */
	private void addPropertySource(org.springframework.core.env.PropertySource<?> propertySource) {
		String name = propertySource.getName();
		/** 1 ******/
		MutablePropertySources propertySources = this.environment.getPropertySources();
		/** 2 ******/
		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			org.springframework.core.env.PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				/** 2.1 ******/
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource rps ?
						rps.withResourceName() : propertySource);
				/** 2.2 ******/
				if (existing instanceof CompositePropertySource cps) {
					cps.addFirstPropertySource(newSource);
				}
				/** 2.3 ******/
				else {
					if (existing instanceof ResourcePropertySource rps) {
						existing = rps.withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					//覆盖环境变量里的资源下名称对应的资源，但是这个名字所对应的资源都还在CompositePropertySource.propertySources中
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		/** 3 ******/
		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		}
		/** 4 ******/
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		/** 5 ******/
		this.propertySourceNames.add(name);
	}

	private PropertySourceFactory instantiateClass(Class<? extends PropertySourceFactory> type) {
		try {
			Constructor<? extends PropertySourceFactory> constructor = type.getDeclaredConstructor();
			ReflectionUtils.makeAccessible(constructor);
			return constructor.newInstance();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to instantiate " + type, ex);
		}
	}

}
