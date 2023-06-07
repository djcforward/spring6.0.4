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

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that implements the {@link DisposableBean} and {@link Runnable}
 * interfaces performing various destruction steps on a given bean instance:
 * <ul>
 * <li>DestructionAwareBeanPostProcessors;
 * <li>the bean implementing DisposableBean itself;
 * <li>a custom destroy method specified on the bean definition.
 * </ul>
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 2.0
 * @see AbstractBeanFactory
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
 * @see AbstractBeanDefinition#getDestroyMethodNames()
 */
@SuppressWarnings("serial")
class DisposableBeanAdapter implements DisposableBean, Runnable, Serializable {

	private static final String DESTROY_METHOD_NAME = "destroy";

	private static final String CLOSE_METHOD_NAME = "close";

	private static final String SHUTDOWN_METHOD_NAME = "shutdown";

	private static final Log logger = LogFactory.getLog(DisposableBeanAdapter.class);


	private final Object bean;

	private final String beanName;

	private final boolean nonPublicAccessAllowed;

	private final boolean invokeDisposableBean;

	private boolean invokeAutoCloseable;

	@Nullable
	private String[] destroyMethodNames;

	@Nullable
	private transient Method[] destroyMethods;

	@Nullable
	private final List<DestructionAwareBeanPostProcessor> beanPostProcessors;


	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 * @param bean the bean instance (never {@code null})
	 * @param beanName the name of the bean
	 * @param beanDefinition the merged bean definition
	 * @param postProcessors the List of BeanPostProcessors
	 * (potentially DestructionAwareBeanPostProcessor), if any
	 */
	public DisposableBeanAdapter(Object bean, String beanName, RootBeanDefinition beanDefinition,
			List<DestructionAwareBeanPostProcessor> postProcessors) {

		Assert.notNull(bean, "Disposable bean must not be null");
		this.bean = bean;
		this.beanName = beanName;
		this.nonPublicAccessAllowed = beanDefinition.isNonPublicAccessAllowed();
		this.invokeDisposableBean = (bean instanceof DisposableBean &&
				!beanDefinition.hasAnyExternallyManagedDestroyMethod(DESTROY_METHOD_NAME));

		String[] destroyMethodNames = inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition);
		if (!ObjectUtils.isEmpty(destroyMethodNames) &&
				!(this.invokeDisposableBean && DESTROY_METHOD_NAME.equals(destroyMethodNames[0])) &&
				!beanDefinition.hasAnyExternallyManagedDestroyMethod(destroyMethodNames[0])) {

			this.invokeAutoCloseable =
					(bean instanceof AutoCloseable && CLOSE_METHOD_NAME.equals(destroyMethodNames[0]));
			if (!this.invokeAutoCloseable) {
				this.destroyMethodNames = destroyMethodNames;
				Method[] destroyMethods = new Method[destroyMethodNames.length];
				for (int i = 0; i < destroyMethodNames.length; i++) {
					String destroyMethodName = destroyMethodNames[i];
					Method destroyMethod = determineDestroyMethod(destroyMethodName);
					if (destroyMethod == null) {
						if (beanDefinition.isEnforceDestroyMethod()) {
							throw new BeanDefinitionValidationException("Could not find a destroy method named '" +
									destroyMethodName + "' on bean with name '" + beanName + "'");
						}
					}
					else {
						if (destroyMethod.getParameterCount() > 0) {
							Class<?>[] paramTypes = destroyMethod.getParameterTypes();
							if (paramTypes.length > 1) {
								throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
										beanName + "' has more than one parameter - not supported as destroy method");
							}
							else if (paramTypes.length == 1 && boolean.class != paramTypes[0]) {
								throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
										beanName + "' has a non-boolean parameter - not supported as destroy method");
							}
						}
						destroyMethod = ClassUtils.getInterfaceMethodIfPossible(destroyMethod, bean.getClass());
					}
					destroyMethods[i] = destroyMethod;
				}
				this.destroyMethods = destroyMethods;
			}
		}

		this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
	}

	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 * @param bean the bean instance (never {@code null})
	 * @param postProcessors the List of BeanPostProcessors
	 * (potentially DestructionAwareBeanPostProcessor), if any
	 */
	public DisposableBeanAdapter(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
		Assert.notNull(bean, "Disposable bean must not be null");
		this.bean = bean;
		this.beanName = bean.getClass().getName();
		this.nonPublicAccessAllowed = true;
		this.invokeDisposableBean = (this.bean instanceof DisposableBean);
		this.beanPostProcessors = filterPostProcessors(postProcessors, bean);
	}

	/**
	 * Create a new DisposableBeanAdapter for the given bean.
	 */
	private DisposableBeanAdapter(Object bean, String beanName, boolean nonPublicAccessAllowed,
			boolean invokeDisposableBean, boolean invokeAutoCloseable, @Nullable String[] destroyMethodNames,
			@Nullable List<DestructionAwareBeanPostProcessor> postProcessors) {

		this.bean = bean;
		this.beanName = beanName;
		this.nonPublicAccessAllowed = nonPublicAccessAllowed;
		this.invokeDisposableBean = invokeDisposableBean;
		this.invokeAutoCloseable = invokeAutoCloseable;
		this.destroyMethodNames = destroyMethodNames;
		this.beanPostProcessors = postProcessors;
	}


	@Override
	public void run() {
		destroy();
	}

	@Override
	public void destroy() {
		/** 处理@PreDestroy标注的方法----begin */
		if (!CollectionUtils.isEmpty(this.beanPostProcessors)) {
			for (DestructionAwareBeanPostProcessor processor : this.beanPostProcessors) {
				//这里我们关心的是InitDestroyAnnotationBeanPostProcessor这个实现类
				processor.postProcessBeforeDestruction(this.bean, this.beanName);
			}
		}
		/** 处理@PreDestroy标注的方法----end */

		if (this.invokeDisposableBean) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking destroy() on bean with name '" + this.beanName + "'");
			}
			try {
				((DisposableBean) this.bean).destroy();
			}
			catch (Throwable ex) {
				String msg = "Invocation of destroy method failed on bean with name '" + this.beanName + "'";
				if (logger.isDebugEnabled()) {
					logger.warn(msg, ex);
				}
				else {
					logger.warn(msg + ": " + ex);
				}
			}
		}

		if (this.invokeAutoCloseable) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking close() on bean with name '" + this.beanName + "'");
			}
			try {
				((AutoCloseable) this.bean).close();
			}
			catch (Throwable ex) {
				String msg = "Invocation of close method failed on bean with name '" + this.beanName + "'";
				if (logger.isDebugEnabled()) {
					logger.warn(msg, ex);
				}
				else {
					logger.warn(msg + ": " + ex);
				}
			}
		}
		/** 调用自定义的销毁逻辑 即@Bean里destroyMethod指定的值 */
		else if (this.destroyMethods != null) {
			for (Method destroyMethod : this.destroyMethods) {
				invokeCustomDestroyMethod(destroyMethod);
			}
		}
		else if (this.destroyMethodNames != null) {
			for (String destroyMethodName: this.destroyMethodNames) {
				Method destroyMethod = determineDestroyMethod(destroyMethodName);
				if (destroyMethod != null) {
					invokeCustomDestroyMethod(
							ClassUtils.getInterfaceMethodIfPossible(destroyMethod, this.bean.getClass()));
				}
			}
		}
	}


	@Nullable
	private Method determineDestroyMethod(String name) {
		try {
			return findDestroyMethod(name);
		}
		catch (IllegalArgumentException ex) {
			throw new BeanDefinitionValidationException("Could not find unique destroy method on bean with name '" +
					this.beanName + ": " + ex.getMessage());
		}
	}

	@Nullable
	private Method findDestroyMethod(String name) {
		return (this.nonPublicAccessAllowed ?
				BeanUtils.findMethodWithMinimalParameters(this.bean.getClass(), name) :
				BeanUtils.findMethodWithMinimalParameters(this.bean.getClass().getMethods(), name));
	}

	/**
	 * Invoke the specified custom destroy method on the given bean.
	 * <p>This implementation invokes a no-arg method if found, else checking
	 * for a method with a single boolean argument (passing in "true",
	 * assuming a "force" parameter), else logging an error.
	 *
	 * 调用自定义的销毁逻辑
	 */
	private void invokeCustomDestroyMethod(Method destroyMethod) {
		int paramCount = destroyMethod.getParameterCount();
		final Object[] args = new Object[paramCount];
		if (paramCount == 1) {
			args[0] = Boolean.TRUE;
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking custom destroy method '" + destroyMethod.getName() +
					"' on bean with name '" + this.beanName + "'");
		}
		try {
			/** 调用自定义的销毁逻辑 */
			ReflectionUtils.makeAccessible(destroyMethod);
			destroyMethod.invoke(this.bean, args);
		}
		catch (InvocationTargetException ex) {
			String msg = "Custom destroy method '" + destroyMethod.getName() + "' on bean with name '" +
					this.beanName + "' threw an exception";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex.getTargetException());
			}
			else {
				logger.warn(msg + ": " + ex.getTargetException());
			}
		}
		catch (Throwable ex) {
			logger.warn("Failed to invoke custom destroy method '" + destroyMethod.getName() +
					"' on bean with name '" + this.beanName + "'", ex);
		}
	}


	/**
	 * Serializes a copy of the state of this class,
	 * filtering out non-serializable BeanPostProcessors.
	 */
	protected Object writeReplace() {
		List<DestructionAwareBeanPostProcessor> serializablePostProcessors = null;
		if (this.beanPostProcessors != null) {
			serializablePostProcessors = new ArrayList<>();
			for (DestructionAwareBeanPostProcessor postProcessor : this.beanPostProcessors) {
				if (postProcessor instanceof Serializable) {
					serializablePostProcessors.add(postProcessor);
				}
			}
		}
		return new DisposableBeanAdapter(
				this.bean, this.beanName, this.nonPublicAccessAllowed, this.invokeDisposableBean,
				this.invokeAutoCloseable, this.destroyMethodNames, serializablePostProcessors);
	}


	/**
	 * Check whether the given bean has any kind of destroy method to call.
	 * @param bean the bean instance
	 * @param beanDefinition the corresponding bean definition
	 */
	public static boolean hasDestroyMethod(Object bean, RootBeanDefinition beanDefinition) {
		return (bean instanceof DisposableBean
				|| inferDestroyMethodsIfNecessary(bean.getClass(), beanDefinition) != null);
	}


	/**
	 * If the current value of the given beanDefinition's "destroyMethodName" property is
	 * {@link AbstractBeanDefinition#INFER_METHOD}, then attempt to infer a destroy method.
	 * Candidate methods are currently limited to public, no-arg methods named "close" or
	 * "shutdown" (whether declared locally or inherited). The given BeanDefinition's
	 * "destroyMethodName" is updated to be null if no such method is found, otherwise set
	 * to the name of the inferred method. This constant serves as the default for the
	 * {@code @Bean#destroyMethod} attribute and the value of the constant may also be
	 * used in XML within the {@code <bean destroy-method="">} or {@code
	 * <beans default-destroy-method="">} attributes.
	 * <p>Also processes the {@link java.io.Closeable} and {@link java.lang.AutoCloseable}
	 * interfaces, reflectively calling the "close" method on implementing beans as well.
	 */
	@Nullable
	static String[] inferDestroyMethodsIfNecessary(Class<?> target, RootBeanDefinition beanDefinition) {
		String[] destroyMethodNames = beanDefinition.getDestroyMethodNames();
		if (destroyMethodNames != null && destroyMethodNames.length > 1) {
			return destroyMethodNames;
		}

		String destroyMethodName = beanDefinition.resolvedDestroyMethodName;
		if (destroyMethodName == null) {
			destroyMethodName = beanDefinition.getDestroyMethodName();
			boolean autoCloseable = (AutoCloseable.class.isAssignableFrom(target));
			if (AbstractBeanDefinition.INFER_METHOD.equals(destroyMethodName) ||
					(destroyMethodName == null && autoCloseable)) {
				// Only perform destroy method inference in case of the bean
				// not explicitly implementing the DisposableBean interface
				destroyMethodName = null;
				if (!(DisposableBean.class.isAssignableFrom(target))) {
					if (autoCloseable) {
						destroyMethodName = CLOSE_METHOD_NAME;
					}
					else {
						try {
							destroyMethodName = target.getMethod(CLOSE_METHOD_NAME).getName();
						}
						catch (NoSuchMethodException ex) {
							try {
								destroyMethodName = target.getMethod(SHUTDOWN_METHOD_NAME).getName();
							}
							catch (NoSuchMethodException ex2) {
								// no candidate destroy method found
							}
						}
					}
				}
			}
			beanDefinition.resolvedDestroyMethodName = (destroyMethodName != null ? destroyMethodName : "");
		}
		return (StringUtils.hasLength(destroyMethodName) ? new String[] {destroyMethodName} : null);
	}

	/**
	 * Check whether the given bean has destruction-aware post-processors applying to it.
	 * @param bean the bean instance
	 * @param postProcessors the post-processor candidates
	 */
	public static boolean hasApplicableProcessors(Object bean, List<DestructionAwareBeanPostProcessor> postProcessors) {
		if (!CollectionUtils.isEmpty(postProcessors)) {
			for (DestructionAwareBeanPostProcessor processor : postProcessors) {
				if (processor.requiresDestruction(bean)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Search for all DestructionAwareBeanPostProcessors in the List.
	 * @param processors the List to search
	 * @return the filtered List of DestructionAwareBeanPostProcessors
	 */
	@Nullable
	private static List<DestructionAwareBeanPostProcessor> filterPostProcessors(
			List<DestructionAwareBeanPostProcessor> processors, Object bean) {

		List<DestructionAwareBeanPostProcessor> filteredPostProcessors = null;
		if (!CollectionUtils.isEmpty(processors)) {
			filteredPostProcessors = new ArrayList<>(processors.size());
			for (DestructionAwareBeanPostProcessor processor : processors) {
				if (processor.requiresDestruction(bean)) {
					filteredPostProcessors.add(processor);
				}
			}
		}
		return filteredPostProcessors;
	}

}
