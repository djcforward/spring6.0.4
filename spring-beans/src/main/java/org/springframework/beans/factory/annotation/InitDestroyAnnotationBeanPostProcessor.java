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

package org.springframework.beans.factory.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that invokes annotated init and destroy methods. Allows for an annotation
 * alternative to Spring's {@link org.springframework.beans.factory.InitializingBean}
 * and {@link org.springframework.beans.factory.DisposableBean} callback interfaces.
 *
 * <p>The actual annotation types that this post-processor checks for can be
 * configured through the {@link #setInitAnnotationType "initAnnotationType"}
 * and {@link #setDestroyAnnotationType "destroyAnnotationType"} properties.
 * Any custom annotation can be used, since there are no required annotation
 * attributes.
 *
 * <p>Init and destroy annotations may be applied to methods of any visibility:
 * public, package-protected, protected, or private. Multiple such methods
 * may be annotated, but it is recommended to only annotate one single
 * init method and destroy method, respectively.
 *
 * <p>Spring's {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor}
 * supports the {@link jakarta.annotation.PostConstruct} and {@link jakarta.annotation.PreDestroy}
 * annotations out of the box, as init annotation and destroy annotation, respectively.
 * Furthermore, it also supports the {@link jakarta.annotation.Resource} annotation
 * for annotation-driven injection of named beans.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 2.5
 * @see #setInitAnnotationType
 * @see #setDestroyAnnotationType
 */
@SuppressWarnings("serial")
public class InitDestroyAnnotationBeanPostProcessor implements DestructionAwareBeanPostProcessor,
		MergedBeanDefinitionPostProcessor, BeanRegistrationAotProcessor, PriorityOrdered, Serializable {

	private final transient LifecycleMetadata emptyLifecycleMetadata =
			new LifecycleMetadata(Object.class, Collections.emptyList(), Collections.emptyList()) {
				@Override
				public void checkConfigMembers(RootBeanDefinition beanDefinition) {
				}
				@Override
				public void invokeInitMethods(Object target, String beanName) {
				}
				@Override
				public void invokeDestroyMethods(Object target, String beanName) {
				}
				@Override
				public boolean hasDestroyMethods() {
					return false;
				}
			};


	protected transient Log logger = LogFactory.getLog(getClass());

	@Nullable
	private Class<? extends Annotation> initAnnotationType;

	@Nullable
	private Class<? extends Annotation> destroyAnnotationType;

	private int order = Ordered.LOWEST_PRECEDENCE;

	@Nullable
	private final transient Map<Class<?>, LifecycleMetadata> lifecycleMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * Specify the init annotation to check for, indicating initialization
	 * methods to call after configuration of a bean.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the {@link jakarta.annotation.PostConstruct} annotation.
	 */
	public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
		this.initAnnotationType = initAnnotationType;
	}

	/**
	 * Specify the destroy annotation to check for, indicating destruction
	 * methods to call when the context is shutting down.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the {@link jakarta.annotation.PreDestroy} annotation.
	 */
	public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
		this.destroyAnnotationType = destroyAnnotationType;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		findInjectionMetadata(beanDefinition, beanType);
	}

	@Override
	public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
		beanDefinition.resolveDestroyMethodIfNecessary();
		LifecycleMetadata metadata = findInjectionMetadata(beanDefinition, registeredBean.getBeanClass());
		if (!CollectionUtils.isEmpty(metadata.initMethods)) {
			String[] initMethodNames = safeMerge(beanDefinition.getInitMethodNames(), metadata.initMethods);
			beanDefinition.setInitMethodNames(initMethodNames);
		}
		if (!CollectionUtils.isEmpty(metadata.destroyMethods)) {
			String[] destroyMethodNames = safeMerge(beanDefinition.getDestroyMethodNames(), metadata.destroyMethods);
			beanDefinition.setDestroyMethodNames(destroyMethodNames);
		}
		return null;
	}

	private LifecycleMetadata findInjectionMetadata(RootBeanDefinition beanDefinition, Class<?> beanType) {
		LifecycleMetadata metadata = findLifecycleMetadata(beanType);
		metadata.checkConfigMembers(beanDefinition);
		return metadata;
	}

	private String[] safeMerge(@Nullable String[] existingNames, Collection<LifecycleElement> detectedElements) {
		Stream<String> detectedNames = detectedElements.stream().map(LifecycleElement::getIdentifier);
		Stream<String> mergedNames = (existingNames != null ?
				Stream.concat(Stream.of(existingNames), detectedNames) : detectedNames);
		return mergedNames.distinct().toArray(String[]::new);
	}

	/**
	 * 方法大体逻辑：
	 * 		1.查找当前类标注了@PostConstruct 或者 @PreDestroy的方法然后
	 * 			封装成LifecycleMetadata，主要是targetClass initMethods destroyMethods这三个属性
	 * 		2.调用当前类的每一个初始化方法
	 *
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return
	 * @throws BeansException
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		/** 1. */
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			/** 2. */
			metadata.invokeInitMethods(bean, beanName);
		}
		catch (InvocationTargetException ex) {
			throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
		}
		return bean;
	}


	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * 方法大体逻辑：
	 * 		1.查找当前类标注了@PostConstruct 或者 @PreDestroy的方法然后
	 * 			封装成LifecycleMetadata，主要是targetClass initMethods destroyMethods这三个属性
	 * 		2.调用当前类的每一个方@PreDestroy方法
	 *
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return
	 * @throws BeansException
	 */
	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
		/** 1. */
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			/** 2. */
			metadata.invokeDestroyMethods(bean, beanName);
		}
		catch (InvocationTargetException ex) {
			String msg = "Destroy method on bean with name '" + beanName + "' threw an exception";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex.getTargetException());
			}
			else {
				logger.warn(msg + ": " + ex.getTargetException());
			}
		}
		catch (Throwable ex) {
			logger.warn("Failed to invoke destroy method on bean with name '" + beanName + "'", ex);
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		return findLifecycleMetadata(bean.getClass()).hasDestroyMethods();
	}

	/**
	 * 方法大体逻辑:
	 * 		1.lifecycleMetadataCache为null，调用buildLifecycleMetadata(),构建生命周期相关的元数据并返回
	 * 		2.lifecycleMetadataCache不为null，从lifecycleMetadataCache中获取metadata
	 * 			如果没获取到还是去调用buildLifecycleMetadata(),构建生命周期相关的元数据并返回，然后存进缓存
	 * 			如果获取到就返回
	 * @param clazz
	 * @return
	 */
	private LifecycleMetadata findLifecycleMetadata(Class<?> clazz) {
		/** 1.lifecycleMetadataCache为null，调用buildLifecycleMetadata(),构建生命周期相关的元数据并返回 */
		if (this.lifecycleMetadataCache == null) {
			// Happens after deserialization, during destruction...
			return buildLifecycleMetadata(clazz);
		}
		/** 2.lifecycleMetadataCache不为null，从lifecycleMetadataCache中获取metadata */
		// Quick check on the concurrent map first, with minimal locking.
		LifecycleMetadata metadata = this.lifecycleMetadataCache.get(clazz);
		if (metadata == null) {
			synchronized (this.lifecycleMetadataCache) {
				metadata = this.lifecycleMetadataCache.get(clazz);
				if (metadata == null) {
					metadata = buildLifecycleMetadata(clazz);
					this.lifecycleMetadataCache.put(clazz, metadata);
				}
				return metadata;
			}
		}
		return metadata;
	}

	/**
	 * 大体逻辑：
	 * 		1.判断当前类的方法有没有@PostConstruct 或者 @PreDestroy，没有就直接返回emptyLifecycleMetadata
	 * 			注意：initAnnotationType、destroyAnnotationType虽然是在当前类定义的，
	 * 				但均是在其子类CommonAnnotationBeanPostProcessor的构造器里设置的
	 * 		2.遍历当前类的所有方法，看是否标注了@PostConstruct 或者 @PreDestroy，如果标注了相应注解，就给他封装成
	 * 			LifecycleElement，最后加入相应的集合。	分别是initMethods，destroyMethods
	 * @param clazz
	 * @return
	 */
	private LifecycleMetadata buildLifecycleMetadata(final Class<?> clazz) {
		/** 1.判断当前类的方法有没有@PostConstruct 或者 @PreDestroy，没有就直接返回emptyLifecycleMetadata */
		if (!AnnotationUtils.isCandidateClass(clazz, Arrays.asList(this.initAnnotationType, this.destroyAnnotationType))) {
			return this.emptyLifecycleMetadata;
		}

		/**
		 * 2.遍历当前类的所有方法，看是否标注了@PostConstruct 或者 @PreDestroy，如果标注了相应注解，就给他封装成
		 * 	 LifecycleElement，最后加入相应的集合。分别是initMethods，destroyMethods
		 */
		List<LifecycleElement> initMethods = new ArrayList<>();
		List<LifecycleElement> destroyMethods = new ArrayList<>();
		Class<?> targetClass = clazz;

		do {
			final List<LifecycleElement> currInitMethods = new ArrayList<>();
			final List<LifecycleElement> currDestroyMethods = new ArrayList<>();

			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
					LifecycleElement element = new LifecycleElement(method);
					currInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Found init method on class [" + clazz.getName() + "]: " + method);
					}
				}
				if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
					currDestroyMethods.add(new LifecycleElement(method));
					if (logger.isTraceEnabled()) {
						logger.trace("Found destroy method on class [" + clazz.getName() + "]: " + method);
					}
				}
			});

			initMethods.addAll(0, currInitMethods);
			destroyMethods.addAll(currDestroyMethods);
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);

		return (initMethods.isEmpty() && destroyMethods.isEmpty() ? this.emptyLifecycleMetadata :
				new LifecycleMetadata(clazz, initMethods, destroyMethods));
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.logger = LogFactory.getLog(getClass());
	}


	/**
	 * Class representing information about annotated init and destroy methods.
	 */
	private class LifecycleMetadata {

		private final Class<?> targetClass;

		private final Collection<LifecycleElement> initMethods;

		private final Collection<LifecycleElement> destroyMethods;

		@Nullable
		private volatile Set<LifecycleElement> checkedInitMethods;

		@Nullable
		private volatile Set<LifecycleElement> checkedDestroyMethods;

		public LifecycleMetadata(Class<?> targetClass, Collection<LifecycleElement> initMethods,
				Collection<LifecycleElement> destroyMethods) {

			this.targetClass = targetClass;
			this.initMethods = initMethods;
			this.destroyMethods = destroyMethods;
		}

		public void checkConfigMembers(RootBeanDefinition beanDefinition) {
			Set<LifecycleElement> checkedInitMethods = new LinkedHashSet<>(this.initMethods.size());
			for (LifecycleElement element : this.initMethods) {
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
					beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
					checkedInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered init method on class [" + this.targetClass.getName() + "]: " + methodIdentifier);
					}
				}
			}
			Set<LifecycleElement> checkedDestroyMethods = new LinkedHashSet<>(this.destroyMethods.size());
			for (LifecycleElement element : this.destroyMethods) {
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
					beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
					checkedDestroyMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered destroy method on class [" + this.targetClass.getName() + "]: " + methodIdentifier);
					}
				}
			}
			this.checkedInitMethods = checkedInitMethods;
			this.checkedDestroyMethods = checkedDestroyMethods;
		}

		public void invokeInitMethods(Object target, String beanName) throws Throwable {
			Collection<LifecycleElement> checkedInitMethods = this.checkedInitMethods;
			Collection<LifecycleElement> initMethodsToIterate =
					(checkedInitMethods != null ? checkedInitMethods : this.initMethods);
			if (!initMethodsToIterate.isEmpty()) {
				for (LifecycleElement element : initMethodsToIterate) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking init method on bean '" + beanName + "': " + element.getMethod());
					}
					//真正去调用
					element.invoke(target);
				}
			}
		}

		public void invokeDestroyMethods(Object target, String beanName) throws Throwable {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			if (!destroyMethodsToUse.isEmpty()) {
				for (LifecycleElement element : destroyMethodsToUse) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking destroy method on bean '" + beanName + "': " + element.getMethod());
					}
					//真正去调用
					element.invoke(target);
				}
			}
		}

		public boolean hasDestroyMethods() {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			return !destroyMethodsToUse.isEmpty();
		}
	}


	/**
	 * Class representing injection information about an annotated method.
	 */
	private static class LifecycleElement {

		private final Method method;

		private final String identifier;

		public LifecycleElement(Method method) {
			if (method.getParameterCount() != 0) {
				throw new IllegalStateException("Lifecycle method annotation requires a no-arg method: " + method);
			}
			this.method = method;
			this.identifier = (Modifier.isPrivate(method.getModifiers()) ?
					ClassUtils.getQualifiedMethodName(method) : method.getName());
		}

		public Method getMethod() {
			return this.method;
		}

		public String getIdentifier() {
			return this.identifier;
		}

		public void invoke(Object target) throws Throwable {//通过反射调用相应的方法
			ReflectionUtils.makeAccessible(this.method);
			this.method.invoke(target, (Object[]) null);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LifecycleElement otherElement)) {
				return false;
			}
			return (this.identifier.equals(otherElement.identifier));
		}

		@Override
		public int hashCode() {
			return this.identifier.hashCode();
		}
	}

}
