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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertySourceDescriptor;
import org.springframework.core.io.support.PropertySourceProcessor;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Predicate;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	@Nullable
	private final PropertySourceRegistry propertySourceRegistry;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	/** 用于存储内部类的 */
	private final ImportStack importStack = new ImportStack();

	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.propertySourceRegistry = (this.environment instanceof ConfigurableEnvironment ce ?
				new PropertySourceRegistry(new PropertySourceProcessor(ce, this.resourceLoader)) : null);
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}

	//在里面都会调用重载方法parse()
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				if (bd instanceof AnnotatedBeanDefinition) {
					//调用重载方法
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					//调用重载方法
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					//调用重载方法
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}

	List<PropertySourceDescriptor> getPropertySourceDescriptors() {
		return (this.propertySourceRegistry != null ? this.propertySourceRegistry.getDescriptors()
				: Collections.emptyList());
	}

	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		//判断当前配置类是否需要跳过
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		if (existingClass != null) {
			if (configClass.isImported()) {
				if (existingClass.isImported()) {
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		/** 递归处理配置类及其父类层次结构(获取配置类及父类的注解信息封装在SourceClass中) -----------begin*/
		// Recursively process the configuration class and its superclass hierarchy.
		SourceClass sourceClass = asSourceClass(configClass, filter);
		do {
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		this.configurationClasses.put(configClass, configClass);
		/** 递归处理配置类及其父类层次结构 -----------end*/
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build 我们指定的那个配置类
	 * @param sourceClass a source class 也是我们指定的那个配置类，但是他具有配置类上的注解信息了
	 * @return the superclass, or {@code null} if none found or previously processed
	 *
	 * 通过读取 {@param configClass} 中的注解、成员和方法来应用处理并构建一个完整的{@link ConfigurationClass}。
	 * 当发现相关来源时，可以多次调用此方法。
	 *
	 * 简单来说 就是去解析相关注解然后将BeanDefinition注入到IOC中
	 *
	 * 大体逻辑：
	 * 		1.如果配置类标记了@Component，处理其内部类
	 * 		2.处理 @PropertySource
	 * 		3.处理 标记@ComponentScan的configuration
	 * 		4.处理 @Import
	 * 	 	5.处理 @ImportResource
	 * 	 	6.处理 @Bean
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {
		/** 1.如果配置类标记了@Component，处理其内部类 */
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// Recursively process any member (nested) classes first
			//递归处理内部类********* 重要
			processMemberClasses(configClass, sourceClass, filter);
		}

		/**处理 @PropertySource-----begin*/
		// Process any @PropertySource annotations
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.propertySourceRegistry != null) {
				//处理逻辑
				this.propertySourceRegistry.processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}
		/**处理 @PropertySource------end*/

		/**处理 标记@ComponentScan的configuration------begin*/
		// Process any @ComponentScan annotations
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// 配置类用了@ComponentScan 注解 -> 立即执行扫描 通过componentScanParser.parse()去解析
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				// 检查任何进一步配置类的扫描定义集，并在需要时递归解析
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}
		/**处理 @ComponentScan------end*/

		/**处理 @Import------begin*/
		// Process any @Import annotations
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);
		/**处理 @Import------end*/

		// Process any @ImportResource annotations
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}
		/**处理 @Bean------begin*/
		// Process individual @Bean methods
		// 检索所有 @Bean 方法的元数据。
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		//遍历每个BeanMethod的元数据，然后创建BeanMethod并添加进beanMethods
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}
		/**处理 @Bean------end*/

		// Process default methods on interfaces
		processInterfaces(configClass, sourceClass);

		// Process superclass, if any
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 * 注册配置类的内部类
	 *
	 * 大体逻辑：
	 * 		1.获取标记了@Component、@Repository等注解的内部类集合
	 * 		2.内部类集合不为空，判断内部类上是否有需要处理的注解，有的话加入candidates
	 * 			2.1 将需要处理的内部类排序
	 * 			2.2 importStack存需要处理的内部类 检查循环依赖
	 * 			2.3 将需要处理的内部类作为配置类，然后继续调用processConfigurationClass()
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {
		/** 获取标记了@Component、@Repository等注解的内部类集合 */
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				/** 2.内部类集合不为空，判断内部类上是否有需要处理的注解，有的话加入candidates */
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			/** 2.1 将需要处理的内部类排序*/
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				/** 2.2 importStack存需要处理的内部类 检查循环依赖*/
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					this.importStack.push(configClass);
					try {
						/** 2.3 将需要处理的内部类作为配置类，然后继续调用processConfigurationClass() */
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 * 检索所有 @Bean 方法的元数据。
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		AnnotationMetadata original = sourceClass.getMetadata();
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	/**
	 * 解析@Import
	 * @param configClass 没有注解信息配置类
	 * @param currentSourceClass 具有注解信息的配置类
	 * @param importCandidates @Import中引入的类
	 * @param exclusionFilter
	 * @param checkForCircularImports
	 *
	 * 方法大致逻辑：
	 * 1.判断是否为空 或者 循环引入
	 * 2.遍历每一个SourceClass candidate，查看他引入的是什么类型，然后处理相应逻辑
	 * 		2.1 处理实现了ImportSelector的类
	 * 		2.2 处理实现了ImportBeanDefinitionRegistrar的类 ，这里只会将他和元数据封装成一个map，然后通过下面的方法去调用并注册
	 * 				ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass()
	 * 		2.3 处理普通类（当成配置类来处理）
	 *
	 * 	注意：实现了ImportSelector的类的的处理方法中的递归逻辑需要好好理解，因为有了这个递归逻辑
	 * 			所以ImportSelector的实现类如果没有实现DeferredImportSelector接口，最后一定
	 * 			会走到第三步，被当成配置类来处理的
	 */
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		/** 1 ****/
		if (importCandidates.isEmpty()) {
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			/** 2 遍历每一个SourceClass candidate，查看他引入的是什么类型，然后处理相应逻辑******/
			this.importStack.push(configClass);
			try {
				for (SourceClass candidate : importCandidates) {
					/**2.1 处理实现了ImportSelector的类***/
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						if (selector instanceof DeferredImportSelector) {
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						else {
							//调用我们实现类的selectImports()，得到我们要注入的BeanName，
							// 对于事务来说，这里的实现类就是AdviceModeImportSelector
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							//这里我们在递归调用的时候 importSourceClasses 用的就是selectImports()返回的Bean
							//但是这些Bean 的类型又有可能是三种类型之一，每种类型对应的逻辑有具体不一样。
							// 所以必须要递归调用，处理每一个返回的Bean
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					/**2.2 处理实现了ImportBeanDefinitionRegistrar的类***/
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						Class<?> candidateClass = candidate.loadClass();
						//生成我们的实现类，将其加入到<ImportBeanDefinitionRegistrar,AnnotationMetadata>这个map中
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					/**2.3 没有实现那两个接口的Bean，走以下逻辑***/
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						//当成一个配置类来处理，可以理解成就是配置类了
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]: " + ex.getMessage(), ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 * 从 {@link ConfigurationClass} 获取 {@link SourceClass} 的工厂方法。
	 * SourceClass ： 包含一些元数据信息（就是获取了标记在配置类上的注解的信息）
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new IOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					deferredImports.forEach(handler::register);
					handler.processGroupImports();
				}
			}
			finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		public void processGroupImports() {
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		/** 将需要处理的内部类作为配置类 */
		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		/**
		 * 因为有些内部类也会标记@Component,@Repository等注解，本方法就是通过反射拿到内部类
		 * 把这些内部类封装成SourceClass然后返回
		 * @return
		 * @throws IOException
		 */
		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class<?> sourceClass) {
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class<?> sourceClass) {
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class<?> sourceClass) {
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new IOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
