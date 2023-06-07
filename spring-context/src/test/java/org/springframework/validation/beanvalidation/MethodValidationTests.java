/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.validation.beanvalidation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Juergen Hoeller
 */
public class MethodValidationTests {

	@Test
	@SuppressWarnings("unchecked")
	public void testMethodValidationInterceptor() {
		MyValidBean bean = new MyValidBean();
		ProxyFactory proxyFactory = new ProxyFactory(bean);
		proxyFactory.addAdvice(new MethodValidationInterceptor());
		proxyFactory.addAdvisor(new AsyncAnnotationAdvisor());
		doTestProxyValidation((MyValidInterface<String>) proxyFactory.getProxy());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMethodValidationPostProcessor() {
		StaticApplicationContext ac = new StaticApplicationContext();
		ac.registerSingleton("mvpp", MethodValidationPostProcessor.class);
		MutablePropertyValues pvs = new MutablePropertyValues();
		pvs.add("beforeExistingAdvisors", false);
		ac.registerSingleton("aapp", AsyncAnnotationBeanPostProcessor.class, pvs);
		ac.registerSingleton("bean", MyValidBean.class);
		ac.refresh();
		doTestProxyValidation(ac.getBean("bean", MyValidInterface.class));
		ac.close();
	}

	private void doTestProxyValidation(MyValidInterface<String> proxy) {
		assertThat(proxy.myValidMethod("value", 5)).isNotNull();
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myValidMethod("value", 15));
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myValidMethod(null, 5));
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myValidMethod("value", 0));
		proxy.myValidAsyncMethod("value", 5);
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myValidAsyncMethod("value", 15));
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myValidAsyncMethod(null, 5));
		assertThat(proxy.myGenericMethod("myValue")).isEqualTo("myValue");
		assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
				proxy.myGenericMethod(null));
	}

	@Test
	public void testLazyValidatorForMethodValidation() {
		@SuppressWarnings("resource")
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				LazyMethodValidationConfig.class, CustomValidatorBean.class,
				MyValidBean.class, MyValidFactoryBean.class);
		ctx.getBeansOfType(MyValidInterface.class).values().forEach(bean -> bean.myValidMethod("value", 5));
	}

	@Test
	public void testLazyValidatorForMethodValidationWithProxyTargetClass() {
		@SuppressWarnings("resource")
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				LazyMethodValidationConfigWithProxyTargetClass.class, CustomValidatorBean.class,
				MyValidBean.class, MyValidFactoryBean.class);
		ctx.getBeansOfType(MyValidInterface.class).values().forEach(bean -> bean.myValidMethod("value", 5));
	}


	@MyStereotype
	public static class MyValidBean implements MyValidInterface<String> {

		@Override
		public Object myValidMethod(String arg1, int arg2) {
			return (arg2 == 0 ? null : "value");
		}

		@Override
		public void myValidAsyncMethod(String arg1, int arg2) {
		}

		@Override
		public String myGenericMethod(String value) {
			return value;
		}
	}


	@MyStereotype
	public static class MyValidFactoryBean implements FactoryBean<String>, MyValidInterface<String> {

		@Override
		public String getObject() {
			return null;
		}

		@Override
		public Class<?> getObjectType() {
			return String.class;
		}

		@Override
		public Object myValidMethod(String arg1, int arg2) {
			return (arg2 == 0 ? null : "value");
		}

		@Override
		public void myValidAsyncMethod(String arg1, int arg2) {
		}

		@Override
		public String myGenericMethod(String value) {
			return value;
		}
	}


	public interface MyValidInterface<T> {

		@NotNull Object myValidMethod(@NotNull(groups = MyGroup.class) String arg1, @Max(10) int arg2);

		@MyValid
		@Async void myValidAsyncMethod(@NotNull(groups = OtherGroup.class) String arg1, @Max(10) int arg2);

		T myGenericMethod(@NotNull T value);
	}


	public interface MyGroup {
	}


	public interface OtherGroup {
	}


	@Validated({MyGroup.class, Default.class})
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MyStereotype {
	}


	@Validated({OtherGroup.class, Default.class})
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MyValid {
	}


	@Configuration
	public static class LazyMethodValidationConfig {

		@Bean
		public static MethodValidationPostProcessor methodValidationPostProcessor(@Lazy Validator validator) {
			MethodValidationPostProcessor postProcessor = new MethodValidationPostProcessor();
			postProcessor.setValidator(validator);
			return postProcessor;
		}
	}


	@Configuration
	public static class LazyMethodValidationConfigWithProxyTargetClass {

		@Bean
		public static MethodValidationPostProcessor methodValidationPostProcessor(@Lazy Validator validator) {
			MethodValidationPostProcessor postProcessor = new MethodValidationPostProcessor();
			postProcessor.setValidator(validator);
			postProcessor.setProxyTargetClass(true);
			return postProcessor;
		}
	}

}
