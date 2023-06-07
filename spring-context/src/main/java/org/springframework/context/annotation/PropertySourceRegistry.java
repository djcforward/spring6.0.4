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

package org.springframework.context.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.PropertySourceProcessor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Registry of {@link PropertySource} processed on configuration classes.
 *
 * @author Stephane Nicoll
 * @since 6.0
 * @see PropertySourceDescriptor
 */
class PropertySourceRegistry {

	private final PropertySourceProcessor propertySourceProcessor;

	private final List<PropertySourceDescriptor> descriptors;

	public PropertySourceRegistry(PropertySourceProcessor propertySourceProcessor) {
		this.propertySourceProcessor = propertySourceProcessor;
		this.descriptors = new ArrayList<>();
	}

	public List<PropertySourceDescriptor> getDescriptors() {
		return Collections.unmodifiableList(this.descriptors);
	}

	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 *
	 * 处理propertySource元数据信息
	 *
	 * 方法大体流程：
	 * 1.获取@PropertySource注解里的元数据，将其封装成一个descriptor
	 * 2.根据descriptor提供的信息，去加载对应的配置文件内容并放到spring的环境变量中
	 * 3.把当前这个descriptor放入一个列表中
	 */
	void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		/** 1 *******/
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		Class<? extends PropertySourceFactory> factorClassToUse =
				(factoryClass != PropertySourceFactory.class ? factoryClass : null);
		PropertySourceDescriptor descriptor = new PropertySourceDescriptor(Arrays.asList(locations), ignoreResourceNotFound, name,
				factorClassToUse, encoding);
		/** 2 ********/
		this.propertySourceProcessor.processPropertySource(descriptor);
		/**3 ************/
		this.descriptors.add(descriptor);
	}

}
