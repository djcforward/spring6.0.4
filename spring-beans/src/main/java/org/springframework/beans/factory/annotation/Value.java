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

package org.springframework.beans.factory.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used at the field or method/constructor parameter level
 * that indicates a default value expression for the annotated element.
 *
 * <p>Typically used for expression-driven or property-driven dependency injection.
 * Also supported for dynamic resolution of handler method arguments &mdash; for
 * example, in Spring MVC.
 *
 * <p>A common use case is to inject values using
 * <code>#{systemProperties.myProp}</code> style SpEL (Spring Expression Language)
 * expressions. Alternatively, values may be injected using
 * <code>${my.app.myProp}</code> style property placeholders.
 *
 * <p>Note that actual processing of the {@code @Value} annotation is performed
 * by a {@link org.springframework.beans.factory.config.BeanPostProcessor
 * BeanPostProcessor} which in turn means that you <em>cannot</em> use
 * {@code @Value} within
 * {@link org.springframework.beans.factory.config.BeanPostProcessor
 * BeanPostProcessor} or
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessor}
 * types. Please consult the javadoc for the {@link AutowiredAnnotationBeanPostProcessor}
 * class (which, by default, checks for the presence of this annotation).
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see AutowiredAnnotationBeanPostProcessor
 * @see Autowired
 * @see org.springframework.beans.factory.config.BeanExpressionResolver
 * @see org.springframework.beans.factory.support.AutowireCandidateResolver#getSuggestedValue
 *
 * 使用场景：
 * 1.可以向Bean的属性注入数据
 * 2.支持Spring的EL表达式
 * 3.可以通过${} 从配置文件中获取值(xml,properties,yaml)
 *
 * 用法总结：
 * 		#{} 用于执行SPEL表达式，将内容赋值给属性
 * 		${} 用于加载配置文件的属性 如果配置文件没有内容会报错，可以通过指定默认值来解决 @Value("${aa.bb:cc}")
 * 		#{} ${} 可以混合使用 但是#{} 必须在外面
 *
 * 涉及的流程：
 * 		实例化阶段，即AbstractApplicationContext.refresh().finishBeanFactoryInitialization()
 *			1.解析并获取标注了@Value的字段
 *				具体在AbstractAutowireCapableBeanFactory.doCreateBean().applyMergedBeanDefinitionPostProcessors()
 *			2.为标注了@Value的字段赋值
 *				具体在AbstractAutowireCapableBeanFactory.doCreateBean().populateBean()
 *			问题：@Value是如何获取属性的值的？
 *				具体在AutowiredFieldElement.inject(),最终是PropertySourcesPropertyResolver.getProperty()
 *
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Value {

	/**
	 * The actual value expression such as <code>#{systemProperties.myProp}</code>
	 * or property placeholder such as <code>${my.app.myProp}</code>.
	 *
	 * 指定要想Bean的属性中注入的数据，数据可以是配置文件中的配置项，并且支持EL表达式
	 */
	String value();

}
