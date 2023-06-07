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

package org.springframework.test.context.support;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link AnnotationConfigContextLoader}.
 *
 * @author Sam Brannen
 * @since 3.1
 */
class AnnotationConfigContextLoaderTests {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

	private final AnnotationConfigContextLoader contextLoader = new AnnotationConfigContextLoader();


	/**
	 * @since 4.0.4
	 */
	@Test
	void loadContextWithConfigContainingLocationsResultsInException() {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(getClass(),
			new String[] { "config.xml" }, EMPTY_CLASS_ARRAY, EMPTY_STRING_ARRAY, contextLoader);
		assertThatIllegalStateException()
			.isThrownBy(() -> contextLoader.loadContext(mergedConfig))
			.withMessageContaining("does not support resource locations");
	}

	/**
	 * @since 6.0
	 */
	@Test
	void loadContextRefreshesContext() throws Exception {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(
				AnnotatedFooConfigInnerClassTestCase.class, EMPTY_STRING_ARRAY,
				new Class<?>[] {AnnotatedFooConfigInnerClassTestCase.FooConfig.class},
				EMPTY_STRING_ARRAY, contextLoader);
		ApplicationContext context = contextLoader.loadContext(mergedConfig);
		assertThat(context).isInstanceOf(ConfigurableApplicationContext.class);
		ConfigurableApplicationContext cac = (ConfigurableApplicationContext) context;
		assertThat(cac.isActive()).as("ApplicationContext is active").isTrue();
		assertThat(context.getBean(String.class)).isEqualTo("foo");
		cac.close();
	}

	/**
	 * @since 6.0
	 */
	@Test
	void loadContextForAotProcessingDoesNotRefreshContext() throws Exception {
		MergedContextConfiguration mergedConfig = new MergedContextConfiguration(
				AnnotatedFooConfigInnerClassTestCase.class, EMPTY_STRING_ARRAY,
				new Class<?>[] {AnnotatedFooConfigInnerClassTestCase.FooConfig.class},
				EMPTY_STRING_ARRAY, contextLoader);
		ApplicationContext context = contextLoader.loadContextForAotProcessing(mergedConfig);
		assertThat(context).isInstanceOf(ConfigurableApplicationContext.class);
		ConfigurableApplicationContext cac = (ConfigurableApplicationContext) context;
		assertThat(cac.isActive()).as("ApplicationContext is active").isFalse();
		assertThat(Arrays.stream(context.getBeanDefinitionNames())).anyMatch(name -> name.contains("FooConfig"));
		cac.close();
	}

	@Test
	void detectDefaultConfigurationClassesForAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(ContextConfigurationInnerClassTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("annotated static ContextConfiguration should be considered.").isEqualTo(1);

		configClasses = contextLoader.detectDefaultConfigurationClasses(AnnotatedFooConfigInnerClassTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("annotated static FooConfig should be considered.").isEqualTo(1);
	}

	@Test
	void detectDefaultConfigurationClassesForMultipleAnnotatedInnerClasses() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(MultipleStaticConfigurationClassesTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("multiple annotated static classes should be considered.").isEqualTo(2);
	}

	@Test
	void detectDefaultConfigurationClassesForNonAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(PlainVanillaFooConfigInnerClassTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("non-annotated static FooConfig should NOT be considered.").isEqualTo(0);
	}

	@Test
	void detectDefaultConfigurationClassesForFinalAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(FinalConfigInnerClassTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("final annotated static Config should NOT be considered.").isEqualTo(0);
	}

	@Test
	void detectDefaultConfigurationClassesForPrivateAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(PrivateConfigInnerClassTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("private annotated inner classes should NOT be considered.").isEqualTo(0);
	}

	@Test
	void detectDefaultConfigurationClassesForNonStaticAnnotatedInnerClass() {
		Class<?>[] configClasses = contextLoader.detectDefaultConfigurationClasses(NonStaticConfigInnerClassesTestCase.class);
		assertThat(configClasses).isNotNull();
		assertThat(configClasses.length).as("non-static annotated inner classes should NOT be considered.").isEqualTo(0);
	}

}
