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

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.PropertySource;
import org.springframework.lang.Nullable;

/**
 * Describe a {@link PropertySource}.
 *
 * @param locations the locations to consider
 * @param ignoreResourceNotFound whether to fail if a location does not exist
 * @param name the name of the property source, or {@code null} to infer one
 * @param propertySourceFactory the {@link PropertySourceFactory} to use, or
 * {@code null} to use the default
 * @param encoding the encoding, or {@code null} to use the default encoding
 * @author Stephane Nicoll
 * @since 6.0
 *
 * 自动创建字段：在定义record类时，您只需要列出字段的名称和类型，而无需显式编写getter、setter和equals/hashCode等方法。
 * 			编译器会自动生成这些方法。
 *
 * 不可变性：Record类的字段是final的，一旦创建后就不能修改。它们是只读的。
 *
 * equals和hashCode方法：Record类自动实现了equals和hashCode方法，它们会基于记录的字段进行比较和哈希计算。
 *
 * toString方法：Record类自动提供了一个toString方法，它将记录的字段值格式化为可读的字符串。
 *
 * 构造函数：Record类自动生成了一个构造函数，用于初始化字段值。您可以通过构造函数的参数列表来指定字段的初始值。
 */
public record PropertySourceDescriptor(List<String> locations, boolean ignoreResourceNotFound,
		@Nullable String name, @Nullable Class<? extends PropertySourceFactory> propertySourceFactory,
		@Nullable String encoding) {

	/**
	 * Create a descriptor with the specified locations.
	 * @param locations the locations to consider
	 */
	public PropertySourceDescriptor(String... locations) {
		this(Arrays.asList(locations), false, null, null, null);
	}

}
