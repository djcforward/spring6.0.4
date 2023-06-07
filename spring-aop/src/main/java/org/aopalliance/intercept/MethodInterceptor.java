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

package org.aopalliance.intercept;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Intercepts calls on an interface on its way to the target. These
 * are nested "on top" of the target.
 *
 * <p>The user should implement the {@link #invoke(MethodInvocation)}
 * method to modify the original behavior. E.g. the following class
 * implements a tracing interceptor (traces all the calls on the
 * intercepted method(s)):
 *
 * <pre class=code>
 * class TracingInterceptor implements MethodInterceptor {
 *   Object invoke(MethodInvocation i) throws Throwable {
 *     System.out.println("method "+i.getMethod()+" is called on "+
 *                        i.getThis()+" with args "+i.getArguments());
 *     Object ret=i.proceed();
 *     System.out.println("method "+i.getMethod()+" returns "+ret);
 *     return ret;
 *   }
 * }
 * </pre>
 *
 * @author Rod Johnson
 */
@FunctionalInterface
public interface MethodInterceptor extends Interceptor {

	/**
	 * Implement this method to perform extra treatments before and
	 * after the invocation. Polite implementations would certainly
	 * like to invoke {@link Joinpoint#proceed()}.
	 * @param invocation the method invocation joinpoint
	 * @return the result of the call to {@link Joinpoint#proceed()};
	 * might be intercepted by the interceptor
	 * @throws Throwable if the interceptors or the target object
	 * throws an exception
	 *
	 * 因为默认ExposeInvocationInterceptor是第一个拦截器，所以先看他的实现方法，他最终又会来到这个方法
	 * 然后根据不同的织入类型去不同的实现方法（Before，After之类的）
	 *
	 * 以下是拦截器的顺序：
	 * @Around 对应的实现类是 AspectJAroundAdvice，方法内部调用ProceedingJoinPoint.proceed(args)会来到这个方法
	 * @Before 对应的实现类是 MethodBeforeAdviceInterceptor
	 * @After 对应的实现类是 AspectJAfterAdvice
	 * @AfterReturning 对应的实现类是 AfterReturningAdviceInterceptor
	 * @AfterThrowing 对应的实现类是 AspectJAfterThrowingAdvice
	 * @Around 对应的实现类是 AspectJAroundAdvice，执行ProceedingJoinPoint.proceed(args)之后的逻辑
	 *
	 * 注意：对于事务来说 对应的实现类是 TransactionInterceptor
	 */
	@Nullable
	Object invoke(@Nonnull MethodInvocation invocation) throws Throwable;

}
