/*
 * Copyright 2002-2020 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a component is only eligible for registration when all
 * {@linkplain #value specified conditions} match.
 *
 * <p>A <em>condition</em> is any state that can be determined programmatically
 * before the bean definition is due to be registered (see {@link Condition} for details).
 *
 * <p>The {@code @Conditional} annotation may be used in any of the following ways:
 * <ul>
 * <li>as a type-level annotation on any class directly or indirectly annotated with
 * {@code @Component}, including {@link Configuration @Configuration} classes</li>
 * <li>as a meta-annotation, for the purpose of composing custom stereotype
 * annotations</li>
 * <li>as a method-level annotation on any {@link Bean @Bean} method</li>
 * </ul>
 *
 * <p>If a {@code @Configuration} class is marked with {@code @Conditional},
 * all of the {@code @Bean} methods, {@link Import @Import} annotations, and
 * {@link ComponentScan @ComponentScan} annotations associated with that
 * class will be subject to the conditions.
 *
 * <p><strong>NOTE</strong>: Inheritance of {@code @Conditional} annotations
 * is not supported; any conditions from superclasses or from overridden
 * methods will not be considered. In order to enforce these semantics,
 * {@code @Conditional} itself is not declared as
 * {@link java.lang.annotation.Inherited @Inherited}; furthermore, any
 * custom <em>composed annotation</em> that is meta-annotated with
 * {@code @Conditional} must not be declared as {@code @Inherited}.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 4.0
 * @see Condition
 *
 * 使用场景：
 * 	1.spring根据不同的环境读取不同的配置信息，通过实现Condition接口，自定义逻辑来判断当前Bean是否匹配当前环境
 * 	2.可以作为类级别的注解直接或间接的与@Component相关联，包括@Configuration的类
 * 	3.可以作为元注解，用于自动编写构造性注解（因为他身上已经有这些注解了）
 * 	4.可以作为方法注解，作用在@Bean标记的方法上（
 * 		注：作用在方法上，BeanMethod一定会被创建并且放到ConfigurationClass的beanMethods中，但是符合条件才会被注入IOC中）
 * 			在哪里判断这个BeanMethod是否符合条件的 ？
 * 			ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod()
 *
 * 	注意：
 * 		配置类上@PropertySource(value = "classpath:test.properties")
 * 				@Conditional(value = {LinuxCondition.class}) 两个注解都有的时候，读不到配置文件
 * 			原因：注册配置类的时候就会去判断@Conditional里指定的class的match()能不能返回true，返回true表示注册
 * 					当前配置类，此时并没有去注册test.properties的信息，所以就没有这个配置文件的内容
 *
 * 		配置类 类上和方法上都有的时候，先执行类上的,配置类符合要求才会去看Bean方法的是否符合要求
 *
 *
 * 涉及流程：
 *   AnnotationConfigApplicationContext.register()阶段，通过conditionEvaluator去查看是否忽略注册当前的Bean
 *   			this.conditionEvaluator.shouldSkip(abd.getMetadata())
 *   AnnotationConfigApplicationContext.refresh().invokeBeanFactoryPostProcessors(beanFactory);
 *   			BeanFactoryProcessor的后置处理逻辑中，判断@Bean标记的方法对应的Bean是否符合@Conditional指定的条件
 *
 *
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {

	/**
	 * All {@link Condition} classes that must {@linkplain Condition#matches match}
	 * in order for the component to be registered.
	 *
	 * 指定实现Condition接口的类，这些实现类要自定义匹配逻辑，
	 * 返回true的就可以匹配成功，就可以被注入
	 *
	 */
	Class<? extends Condition>[] value();

}
