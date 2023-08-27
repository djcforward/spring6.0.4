/*
 * Copyright 2002-2018 the original author or authors.
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
 * Beans on which the current bean depends. Any beans specified are guaranteed to be
 * created by the container before this bean. Used infrequently in cases where a bean
 * does not explicitly depend on another through properties or constructor arguments,
 * but rather depends on the side effects of another bean's initialization.
 *
 * <p>A depends-on declaration can specify both an initialization-time dependency and,
 * in the case of singleton beans only, a corresponding destruction-time dependency.
 * Dependent beans that define a depends-on relationship with a given bean are destroyed
 * first, prior to the given bean itself being destroyed. Thus, a depends-on declaration
 * can also control shutdown order.
 *
 * <p>May be used on any class directly or indirectly annotated with
 * {@link org.springframework.stereotype.Component} or on methods annotated
 * with {@link Bean}.
 *
 * <p>Using {@link DependsOn} at the class level has no effect unless component-scanning
 * is being used. If a {@link DependsOn}-annotated class is declared via XML,
 * {@link DependsOn} annotation metadata is ignored, and
 * {@code <bean depends-on="..."/>} is respected instead.
 *
 * @author Juergen Hoeller
 * @since 3.0
 *
 * 指定创建Bean的依赖顺序，比如Spring需要创建A和B，我们在A的上面标记@DependsOn(B)，就会先创建B
 *
 * 使用场景：
 * 		1.某些时候，A不是通过属性或者构造器显示依赖B，但是创建A之前必须创建B，此时就可以用@DependsOn
 * 		2.在单例Bean的情况下@DependsOn既可以指定初始化依赖顺序，也可以指定相应的销毁执行顺序
 * 		3.@DependsOn既可以直接或间接标注在@Component标记的Bean上，@Bean标记的方法上，可以控制Bean的创建，初始化，销毁方法执行顺序
 * 		4.观察者模式可以分为事件，事件源、监听器三个组件，如果在Spring中需要实现观察者模式时，可以使用@DependsOn实现监听器的Bean对象
 * 			在事件源的Bean对象之前被创建。
 *
 * 涉及流程：
 * （1）register()阶段，查看指定的配置类有没有标注@DependsOn，然后注册Bean信息
 * 			AnnotationConfigApplicationContext.register(componentClasses);
 * （2）refresh()，调用BeanFactoryPostProcessor的后置处理逻辑，即invokeBeanFactoryPostProcessors()
 * 			通过@CommponentScan去扫描相应路径，然后判断哪些Bean有没有标注@DependsOn，
 * 			如果标注了就封装Bean信息 AbstractApplicationContext.refresh();
 * 上面两步最终调用的逻辑都是AnnotationConfigUtils.processCommonDefinitionAnnotations() 且属于容器的器启动阶段
 * （3）	refresh()，实例化阶段,即 finishBeanFactoryInitialization(beanFactory);
 *
 *
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DependsOn {

	//Bean的名字，被指向的Bean会先创建
	String[] value() default {};

}
