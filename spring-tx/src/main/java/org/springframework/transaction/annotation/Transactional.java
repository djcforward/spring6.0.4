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

package org.springframework.transaction.annotation;

import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.TransactionDefinition;

import java.lang.annotation.*;

/**
 * Describes a transaction attribute on an individual method or on a class.
 *
 * <p>When this annotation is declared at the class level, it applies as a default
 * to all methods of the declaring class and its subclasses. Note that it does not
 * apply to ancestor classes up the class hierarchy; inherited methods need to be
 * locally redeclared in order to participate in a subclass-level annotation. For
 * details on method visibility constraints, consult the
 * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction">Transaction Management</a>
 * section of the reference manual.
 *
 * <p>This annotation is generally directly comparable to Spring's
 * {@link org.springframework.transaction.interceptor.RuleBasedTransactionAttribute}
 * class, and in fact {@link AnnotationTransactionAttributeSource} will directly
 * convert this annotation's attributes to properties in {@code RuleBasedTransactionAttribute},
 * so that Spring's transaction support code does not have to know about annotations.
 *
 * <h3>Attribute Semantics</h3>
 *
 * <p>If no custom rollback rules are configured in this annotation, the transaction
 * will roll back on {@link RuntimeException} and {@link Error} but not on checked
 * exceptions.
 *
 * <p>Rollback rules determine if a transaction should be rolled back when a given
 * exception is thrown, and the rules are based on types or patterns. Custom
 * rules may be configured via {@link #rollbackFor}/{@link #noRollbackFor} and
 * {@link #rollbackForClassName}/{@link #noRollbackForClassName}, which allow
 * rules to be specified as types or patterns, respectively.
 *
 * <p>When a rollback rule is defined with an exception type, that type will be
 * used to match against the type of a thrown exception and its super types,
 * providing type safety and avoiding any unintentional matches that may occur
 * when using a pattern. For example, a value of
 * {@code jakarta.servlet.ServletException.class} will only match thrown exceptions
 * of type {@code jakarta.servlet.ServletException} and its subclasses.
 *
 * <p>When a rollback rule is defined with an exception pattern, the pattern can
 * be a fully qualified class name or a substring of a fully qualified class name
 * for an exception type (which must be a subclass of {@code Throwable}), with no
 * wildcard support at present. For example, a value of
 * {@code "jakarta.servlet.ServletException"} or {@code "ServletException"} will
 * match {@code jakarta.servlet.ServletException} and its subclasses.
 *
 * <p><strong>WARNING:</strong> You must carefully consider how specific a pattern
 * is and whether to include package information (which isn't mandatory). For example,
 * {@code "Exception"} will match nearly anything and will probably hide other
 * rules. {@code "java.lang.Exception"} would be correct if {@code "Exception"}
 * were meant to define a rule for all checked exceptions. With more unique
 * exception names such as {@code "BaseBusinessException"} there is likely no
 * need to use the fully qualified class name for the exception pattern. Furthermore,
 * rollback rules defined via patterns may result in unintentional matches for
 * similarly named exceptions and nested classes. This is due to the fact that a
 * thrown exception is considered to be a match for a given pattern-based rollback
 * rule if the name of thrown exception contains the exception pattern configured
 * for the rollback rule. For example, given a rule configured to match against
 * {@code "com.example.CustomException"}, that rule will match against an exception
 * named {@code com.example.CustomExceptionV2} (an exception in the same package as
 * {@code CustomException} but with an additional suffix) or an exception named
 * {@code com.example.CustomException$AnotherException} (an exception declared as
 * a nested class in {@code CustomException}).
 *
 * <p>For specific information about the semantics of other attributes in this
 * annotation, consult the {@link org.springframework.transaction.TransactionDefinition}
 * and {@link org.springframework.transaction.interceptor.TransactionAttribute} javadocs.
 *
 * <h3>Transaction Management</h3>
 *
 * <p>This annotation commonly works with thread-bound transactions managed by a
 * {@link org.springframework.transaction.PlatformTransactionManager}, exposing a
 * transaction to all data access operations within the current execution thread.
 * <b>Note: This does NOT propagate to newly started threads within the method.</b>
 *
 * <p>Alternatively, this annotation may demarcate a reactive transaction managed
 * by a {@link org.springframework.transaction.ReactiveTransactionManager} which
 * uses the Reactor context instead of thread-local variables. As a consequence,
 * all participating data access operations need to execute within the same
 * Reactor context in the same reactive pipeline.
 *
 * @author Colin Sampaleanu
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Mark Paluch
 * @since 1.2
 * @see org.springframework.transaction.interceptor.TransactionAttribute
 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute
 * @see org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
 *
 * 使用场景：
 * 		当标注到接口上时，实现了接口的方法都是事务
 * 		当标注到类上是，当前类的所有方法都是事务
 * 		当标注到方法上时，仅当前方法是事务
 *
 * 	优先级：方法 > 类 > 接口
 *
 * 涉及的流程：
 * 		Bean实例化之前，获取事务的属性，并对Bean进行增强
 * 			AbstractAutoProxyCreator.wrapIfNecessary()
 * 			最终调用SpringTransactionAnnotationParser.parseTransactionAnnotation()
 *
 * 执行事务的整体流程：
 * 		TransactionAspectSupport.invokeWithinTransaction()
 *
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Reflective
public @interface Transactional {

	/**
	 * Alias for {@link #transactionManager}.
	 * @see #transactionManager
	 *
	 * 事务管理器的唯一标识
	 */
	@AliasFor("transactionManager")
	String value() default "";

	/**
	 * A <em>qualifier</em> value for the specified transaction.
	 * <p>May be used to determine the target transaction manager, matching the
	 * qualifier value (or the bean name) of a specific
	 * {@link org.springframework.transaction.TransactionManager TransactionManager}
	 * bean definition.
	 * @since 4.2
	 * @see #value
	 * @see org.springframework.transaction.PlatformTransactionManager
	 * @see org.springframework.transaction.ReactiveTransactionManager
	 *
	 * 事务管理器的唯一标识
	 */
	@AliasFor("value")
	String transactionManager() default "";

	/**
	 * Defines zero (0) or more transaction labels.
	 * <p>Labels may be used to describe a transaction, and they can be evaluated
	 * by individual transaction managers. Labels may serve a solely descriptive
	 * purpose or map to pre-defined transaction manager-specific options.
	 * <p>See the documentation of the actual transaction manager implementation
	 * for details on how it evaluates transaction labels.
	 * @since 5.3
	 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute#getLabels()
	 *
	 * 设置属性的标签
	 */
	String[] label() default {};

	/**
	 * The transaction propagation type.
	 * <p>Defaults to {@link Propagation#REQUIRED}.
	 * @see org.springframework.transaction.interceptor.TransactionAttribute#getPropagationBehavior()
	 *
	 * 指定事务的传播行为
	 */
	Propagation propagation() default Propagation.REQUIRED;

	/**
	 * The transaction isolation level.
	 * <p>Defaults to {@link Isolation#DEFAULT}.
	 * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
	 * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
	 * transactions. Consider switching the "validateExistingTransactions" flag to
	 * "true" on your transaction manager if you'd like isolation level declarations
	 * to get rejected when participating in an existing transaction with a different
	 * isolation level.
	 * @see org.springframework.transaction.interceptor.TransactionAttribute#getIsolationLevel()
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setValidateExistingTransaction
	 *
	 * 指定事务的隔离级别
	 */
	Isolation isolation() default Isolation.DEFAULT;

	/**
	 * The timeout for this transaction (in seconds).
	 * <p>Defaults to the default timeout of the underlying transaction system.
	 * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
	 * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
	 * transactions.
	 * @return the timeout in seconds
	 * @see org.springframework.transaction.interceptor.TransactionAttribute#getTimeout()
	 *
	 * 指定事务的超时时间，单位为秒，超时就会触发超时回滚操作
	 */
	int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;

	/**
	 * The timeout for this transaction (in seconds).
	 * <p>Defaults to the default timeout of the underlying transaction system.
	 * <p>Exclusively designed for use with {@link Propagation#REQUIRED} or
	 * {@link Propagation#REQUIRES_NEW} since it only applies to newly started
	 * transactions.
	 * @return the timeout in seconds as a String value, e.g. a placeholder
	 * @since 5.3
	 * @see org.springframework.transaction.interceptor.TransactionAttribute#getTimeout()
	 *
	 * 通过String来指定事务的超时时间
	 */
	String timeoutString() default "";

	/**
	 * A boolean flag that can be set to {@code true} if the transaction is
	 * effectively read-only, allowing for corresponding optimizations at runtime.
	 * <p>Defaults to {@code false}.
	 * <p>This just serves as a hint for the actual transaction subsystem;
	 * it will <i>not necessarily</i> cause failure of write access attempts.
	 * A transaction manager which cannot interpret the read-only hint will
	 * <i>not</i> throw an exception when asked for a read-only transaction
	 * but rather silently ignore the hint.
	 * @see org.springframework.transaction.interceptor.TransactionAttribute#isReadOnly()
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isCurrentTransactionReadOnly()
	 *
	 * 指定是否为只读事务，true是只读事务
	 */
	boolean readOnly() default false;

	/**
	 * Defines zero (0) or more exception {@linkplain Class types}, which must be
	 * subclasses of {@link Throwable}, indicating which exception types must cause
	 * a transaction rollback.
	 * <p>By default, a transaction will be rolled back on {@link RuntimeException}
	 * and {@link Error} but not on checked exceptions (business exceptions). See
	 * {@link org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)}
	 * for a detailed explanation.
	 * <p>This is the preferred way to construct a rollback rule (in contrast to
	 * {@link #rollbackForClassName}), matching the exception type and its subclasses
	 * in a type-safe manner. See the {@linkplain Transactional class-level javadocs}
	 * for further details on rollback rule semantics.
	 * @see #rollbackForClassName
	 * @see org.springframework.transaction.interceptor.RollbackRuleAttribute#RollbackRuleAttribute(Class)
	 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)
	 *
	 * 指定异常类的Class对象，当抛出指定类型的异常或其子类型的异常时，事务会自动回滚
	 */
	Class<? extends Throwable>[] rollbackFor() default {};

	/**
	 * Defines zero (0) or more exception name patterns (for exceptions which must be a
	 * subclass of {@link Throwable}), indicating which exception types must cause
	 * a transaction rollback.
	 * <p>See the {@linkplain Transactional class-level javadocs} for further details
	 * on rollback rule semantics, patterns, and warnings regarding possible
	 * unintentional matches.
	 * @see #rollbackFor
	 * @see org.springframework.transaction.interceptor.RollbackRuleAttribute#RollbackRuleAttribute(String)
	 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)
	 *
	 * 指定异常类的全类名，当抛出指定的全类名或者其子类型的异常时，事务会自动回滚
	 */
	String[] rollbackForClassName() default {};

	/**
	 * Defines zero (0) or more exception {@link Class types}, which must be
	 * subclasses of {@link Throwable}, indicating which exception types must
	 * <b>not</b> cause a transaction rollback.
	 * <p>This is the preferred way to construct a rollback rule (in contrast to
	 * {@link #noRollbackForClassName}), matching the exception type and its subclasses
	 * in a type-safe manner. See the {@linkplain Transactional class-level javadocs}
	 * for further details on rollback rule semantics.
	 * @see #noRollbackForClassName
	 * @see org.springframework.transaction.interceptor.NoRollbackRuleAttribute#NoRollbackRuleAttribute(Class)
	 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)
	 *
	 * 指定异常类的Class对象，当抛出指定类型的异常或其子类型的异常时，事务不会自动回滚
	 */
	Class<? extends Throwable>[] noRollbackFor() default {};

	/**
	 * Defines zero (0) or more exception name patterns (for exceptions which must be a
	 * subclass of {@link Throwable}) indicating which exception types must <b>not</b>
	 * cause a transaction rollback.
	 * <p>See the {@linkplain Transactional class-level javadocs} for further details
	 * on rollback rule semantics, patterns, and warnings regarding possible
	 * unintentional matches.
	 * @see #noRollbackFor
	 * @see org.springframework.transaction.interceptor.NoRollbackRuleAttribute#NoRollbackRuleAttribute(String)
	 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)
	 *
	 * 指定异常类的全类名，当抛出指定的全类名或者其子类型的异常时，事务不会自动回滚
	 */
	String[] noRollbackForClassName() default {};

}
