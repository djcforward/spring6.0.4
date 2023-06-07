/*
 * Copyright 2002-2011 the original author or authors.
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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation may be used on a field or parameter as a qualifier for
 * candidate beans when autowiring. It may also be used to annotate other
 * custom annotations that can then in turn be used as qualifiers.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 2.5
 * @see Autowired
 *
 * 使用场景：
 * 	当存在多个类型匹配但名称不匹配Bean时，此时@Autowired就不管用了，需要@Qualier来指定BeanId来注入
 *
 * 	 ** 涉及的流程：
 *     	实例化阶段，即AbstractApplicationContext.refresh().finishBeanFactoryInitialization()
 *    			1.解析并获取标注了@Autowired的字段
 *    				具体在AbstractAutowireCapableBeanFactory.doCreateBean().applyMergedBeanDefinitionPostProcessors()
 *    			2.@Autowired的字段是如何获取属性的值的
 *    				具体在AutowiredFieldElement.inject()
 *  			3.填充标注了@Qualifier和@Autowired的字段
 *    				具体在AbstractAutowireCapableBeanFactory.doCreateBean().populateBean()
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Qualifier {

	//BeanId
	String value() default "";

}
