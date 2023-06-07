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

package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Rob Harrop
 * @author Rick Evans
 * @author Juergen Hoeller
 * @author Chris Beams
 */
public class CustomizableTraceInterceptorTests {

	@Test
	public void testSetEmptyEnterMessage() {
		// Must not be able to set empty enter message
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setEnterMessage(""));
	}

	@Test
	public void testSetEnterMessageWithReturnValuePlaceholder() {
		// Must not be able to set enter message with return value placeholder
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setEnterMessage(CustomizableTraceInterceptor.PLACEHOLDER_RETURN_VALUE));
	}

	@Test
	public void testSetEnterMessageWithExceptionPlaceholder() {
		// Must not be able to set enter message with exception placeholder
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setEnterMessage(CustomizableTraceInterceptor.PLACEHOLDER_EXCEPTION));
	}

	@Test
	public void testSetEnterMessageWithInvocationTimePlaceholder() {
		// Must not be able to set enter message with invocation time placeholder
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setEnterMessage(CustomizableTraceInterceptor.PLACEHOLDER_INVOCATION_TIME));
	}

	@Test
	public void testSetEmptyExitMessage() {
		// Must not be able to set empty exit message
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setExitMessage(""));
	}

	@Test
	public void testSetExitMessageWithExceptionPlaceholder() {
		// Must not be able to set exit message with exception placeholder
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setExitMessage(CustomizableTraceInterceptor.PLACEHOLDER_EXCEPTION));
	}

	@Test
	public void testSetEmptyExceptionMessage() {
		// Must not be able to set empty exception message
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setExceptionMessage(""));
	}

	@Test
	public void testSetExceptionMethodWithReturnValuePlaceholder() {
		// Must not be able to set exception message with return value placeholder
		assertThatIllegalArgumentException().isThrownBy(() ->
				new CustomizableTraceInterceptor().setExceptionMessage(CustomizableTraceInterceptor.PLACEHOLDER_RETURN_VALUE));
	}

	@Test
	public void testSunnyDayPathLogsCorrectly() throws Throwable {

		MethodInvocation methodInvocation = mock(MethodInvocation.class);
		given(methodInvocation.getMethod()).willReturn(String.class.getMethod("toString"));
		given(methodInvocation.getThis()).willReturn(this);

		Log log = mock(Log.class);
		given(log.isTraceEnabled()).willReturn(true);

		CustomizableTraceInterceptor interceptor = new StubCustomizableTraceInterceptor(log);
		interceptor.invoke(methodInvocation);

		verify(log, times(2)).trace(anyString());
	}

	@Test
	public void testExceptionPathLogsCorrectly() throws Throwable {

		MethodInvocation methodInvocation = mock(MethodInvocation.class);

		IllegalArgumentException exception = new IllegalArgumentException();
		given(methodInvocation.getMethod()).willReturn(String.class.getMethod("toString"));
		given(methodInvocation.getThis()).willReturn(this);
		given(methodInvocation.proceed()).willThrow(exception);

		Log log = mock(Log.class);
		given(log.isTraceEnabled()).willReturn(true);

		CustomizableTraceInterceptor interceptor = new StubCustomizableTraceInterceptor(log);
		assertThatIllegalArgumentException().isThrownBy(() ->
				interceptor.invoke(methodInvocation));

		verify(log).trace(anyString());
		verify(log).trace(anyString(), eq(exception));
	}

	@Test
	public void testSunnyDayPathLogsCorrectlyWithPrettyMuchAllPlaceholdersMatching() throws Throwable {

		MethodInvocation methodInvocation = mock(MethodInvocation.class);

		given(methodInvocation.getMethod()).willReturn(String.class.getMethod("toString", new Class[0]));
		given(methodInvocation.getThis()).willReturn(this);
		given(methodInvocation.getArguments()).willReturn(new Object[]{"$ One \\$", 2L});
		given(methodInvocation.proceed()).willReturn("Hello!");

		Log log = mock(Log.class);
		given(log.isTraceEnabled()).willReturn(true);

		CustomizableTraceInterceptor interceptor = new StubCustomizableTraceInterceptor(log);
		interceptor.setEnterMessage(new StringBuilder()
			.append("Entering the '").append(CustomizableTraceInterceptor.PLACEHOLDER_METHOD_NAME)
			.append("' method of the [").append(CustomizableTraceInterceptor.PLACEHOLDER_TARGET_CLASS_NAME)
			.append("] class with the following args (").append(CustomizableTraceInterceptor.PLACEHOLDER_ARGUMENTS)
			.append(") and arg types (").append(CustomizableTraceInterceptor.PLACEHOLDER_ARGUMENT_TYPES)
			.append(").").toString());
		interceptor.setExitMessage(new StringBuilder()
			.append("Exiting the '").append(CustomizableTraceInterceptor.PLACEHOLDER_METHOD_NAME)
			.append("' method of the [").append(CustomizableTraceInterceptor.PLACEHOLDER_TARGET_CLASS_SHORT_NAME)
			.append("] class with the following args (").append(CustomizableTraceInterceptor.PLACEHOLDER_ARGUMENTS)
			.append(") and arg types (").append(CustomizableTraceInterceptor.PLACEHOLDER_ARGUMENT_TYPES)
			.append("), returning '").append(CustomizableTraceInterceptor.PLACEHOLDER_RETURN_VALUE)
			.append("' and taking '").append(CustomizableTraceInterceptor.PLACEHOLDER_INVOCATION_TIME)
			.append("' this long.").toString());
		interceptor.invoke(methodInvocation);

		verify(log, times(2)).trace(anyString());
	}


	@SuppressWarnings("serial")
	private static class StubCustomizableTraceInterceptor extends CustomizableTraceInterceptor {

		private final Log log;

		public StubCustomizableTraceInterceptor(Log log) {
			super.setUseDynamicLogger(false);
			this.log = log;
		}

		@Override
		protected Log getLoggerForInvocation(MethodInvocation invocation) {
			return this.log;
		}
	}

}
