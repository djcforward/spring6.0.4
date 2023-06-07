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

package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.lang.model.element.Modifier;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.generate.MethodReference.ArgumentCodeGenerator;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.testfixture.beans.TestBean;
import org.springframework.beans.testfixture.beans.factory.aot.MockBeanFactoryInitializationCode;
import org.springframework.core.test.io.support.MockSpringFactoriesLoader;
import org.springframework.core.test.tools.Compiled;
import org.springframework.core.test.tools.SourceFile;
import org.springframework.core.test.tools.TestCompiler;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.javapoet.MethodSpec;
import org.springframework.javapoet.ParameterizedTypeName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.aot.BeanRegistrationsAotContribution.Registration;

/**
 * Tests for {@link BeanRegistrationsAotContribution}.
 *
 * @author Phillip Webb
 * @author Sebastien Deleuze
 * @author Stephane Nicoll
 */
class BeanRegistrationsAotContributionTests {

	private final DefaultListableBeanFactory beanFactory;

	private final BeanDefinitionMethodGeneratorFactory methodGeneratorFactory;

	private TestGenerationContext generationContext;

	private MockBeanFactoryInitializationCode beanFactoryInitializationCode;


	BeanRegistrationsAotContributionTests() {
		MockSpringFactoriesLoader springFactoriesLoader = new MockSpringFactoriesLoader();
		this.beanFactory = new DefaultListableBeanFactory();
		this.methodGeneratorFactory = new BeanDefinitionMethodGeneratorFactory(
				AotServices.factoriesAndBeans(springFactoriesLoader, this.beanFactory));
		this.generationContext = new TestGenerationContext();
		this.beanFactoryInitializationCode = new MockBeanFactoryInitializationCode(this.generationContext);
	}


	@Test
	void applyToAppliesContribution() {
		RegisteredBean registeredBean = registerBean(
				new RootBeanDefinition(TestBean.class));
		BeanDefinitionMethodGenerator generator = new BeanDefinitionMethodGenerator(
				this.methodGeneratorFactory, registeredBean, null,
				Collections.emptyList());
		BeanRegistrationsAotContribution contribution = createContribution(generator);
		contribution.applyTo(this.generationContext, this.beanFactoryInitializationCode);
		compile((consumer, compiled) -> {
			DefaultListableBeanFactory freshBeanFactory = new DefaultListableBeanFactory();
			consumer.accept(freshBeanFactory);
			assertThat(freshBeanFactory.getBean(TestBean.class)).isNotNull();
		});
	}

	@Test
	void applyToAppliesContributionWithAliases() {
		RegisteredBean registeredBean = registerBean(
				new RootBeanDefinition(TestBean.class));
		BeanDefinitionMethodGenerator generator = new BeanDefinitionMethodGenerator(
				this.methodGeneratorFactory, registeredBean, null,
				Collections.emptyList());
		BeanRegistrationsAotContribution contribution = createContribution(generator, "testAlias");
		contribution.applyTo(this.generationContext, this.beanFactoryInitializationCode);
		compile((consumer, compiled) -> {
			DefaultListableBeanFactory freshBeanFactory = new DefaultListableBeanFactory();
			consumer.accept(freshBeanFactory);
			assertThat(freshBeanFactory.getAliases("testBean")).containsExactly("testAlias");
		});
	}

	@Test
	void applyToWhenHasNameGeneratesPrefixedFeatureName() {
		this.generationContext = new TestGenerationContext(
				new ClassNameGenerator(TestGenerationContext.TEST_TARGET, "Management"));
		this.beanFactoryInitializationCode = new MockBeanFactoryInitializationCode(this.generationContext);
		RegisteredBean registeredBean = registerBean(
				new RootBeanDefinition(TestBean.class));
		BeanDefinitionMethodGenerator generator = new BeanDefinitionMethodGenerator(
				this.methodGeneratorFactory, registeredBean, null,
				Collections.emptyList());
		BeanRegistrationsAotContribution contribution = createContribution(generator);
		contribution.applyTo(this.generationContext, this.beanFactoryInitializationCode);
		compile((consumer, compiled) -> {
			SourceFile sourceFile = compiled.getSourceFile(".*BeanDefinitions");
			assertThat(sourceFile.getClassName()).endsWith("__ManagementBeanDefinitions");
		});
	}

	@Test
	void applyToCallsRegistrationsWithBeanRegistrationsCode() {
		List<BeanRegistrationsCode> beanRegistrationsCodes = new ArrayList<>();
		RegisteredBean registeredBean = registerBean(
				new RootBeanDefinition(TestBean.class));
		BeanDefinitionMethodGenerator generator = new BeanDefinitionMethodGenerator(
				this.methodGeneratorFactory, registeredBean, null,
				Collections.emptyList()) {

			@Override
			MethodReference generateBeanDefinitionMethod(
					GenerationContext generationContext,
					BeanRegistrationsCode beanRegistrationsCode) {
				beanRegistrationsCodes.add(beanRegistrationsCode);
				return super.generateBeanDefinitionMethod(generationContext,
						beanRegistrationsCode);
			}

		};
		BeanRegistrationsAotContribution contribution = createContribution(generator);
		contribution.applyTo(this.generationContext, this.beanFactoryInitializationCode);
		assertThat(beanRegistrationsCodes).hasSize(1);
		BeanRegistrationsCode actual = beanRegistrationsCodes.get(0);
		assertThat(actual.getMethods()).isNotNull();
	}

	private RegisteredBean registerBean(RootBeanDefinition rootBeanDefinition) {
		String beanName = "testBean";
		this.beanFactory.registerBeanDefinition(beanName, rootBeanDefinition);
		return RegisteredBean.of(this.beanFactory, beanName);
	}

	@SuppressWarnings({ "unchecked", "cast" })
	private void compile(
			BiConsumer<Consumer<DefaultListableBeanFactory>, Compiled> result) {
		MethodReference beanRegistrationsMethodReference = this.beanFactoryInitializationCode
				.getInitializers().get(0);
		MethodReference aliasesMethodReference = this.beanFactoryInitializationCode
				.getInitializers().get(1);
		this.beanFactoryInitializationCode.getTypeBuilder().set(type -> {
			ArgumentCodeGenerator beanFactory = ArgumentCodeGenerator.of(DefaultListableBeanFactory.class, "beanFactory");
			ClassName className = this.beanFactoryInitializationCode.getClassName();
			CodeBlock beanRegistrationsMethodInvocation = beanRegistrationsMethodReference.toInvokeCodeBlock(beanFactory, className);
			CodeBlock aliasesMethodInvocation = aliasesMethodReference.toInvokeCodeBlock(beanFactory, className);
			type.addModifiers(Modifier.PUBLIC);
			type.addSuperinterface(ParameterizedTypeName.get(Consumer.class, DefaultListableBeanFactory.class));
			type.addMethod(MethodSpec.methodBuilder("accept").addModifiers(Modifier.PUBLIC)
					.addParameter(DefaultListableBeanFactory.class, "beanFactory")
					.addStatement(beanRegistrationsMethodInvocation)
					.addStatement(aliasesMethodInvocation)
					.build());
		});
		this.generationContext.writeGeneratedContent();
		TestCompiler.forSystem().with(this.generationContext).compile(compiled ->
				result.accept(compiled.getInstance(Consumer.class), compiled));
	}

	private BeanRegistrationsAotContribution createContribution(
			BeanDefinitionMethodGenerator methodGenerator,String... aliases) {
		return new BeanRegistrationsAotContribution(Map.of("testBean", new Registration(methodGenerator, aliases)));
	}

}
