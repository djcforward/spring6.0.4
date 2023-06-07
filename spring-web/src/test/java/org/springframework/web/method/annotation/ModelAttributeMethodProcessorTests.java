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

package org.springframework.web.method.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.testfixture.beans.TestBean;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test fixture with {@link ModelAttributeMethodProcessor}.
 *
 * @author Rossen Stoyanchev
 * @author Vladislav Kisel
 */
public class ModelAttributeMethodProcessorTests {

	private NativeWebRequest request;

	private ModelAndViewContainer container;

	private ModelAttributeMethodProcessor processor;

	private MethodParameter paramNamedValidModelAttr;
	private MethodParameter paramErrors;
	private MethodParameter paramInt;
	private MethodParameter paramModelAttr;
	private MethodParameter paramBindingDisabledAttr;
	private MethodParameter paramNonSimpleType;
	private MethodParameter beanWithConstructorArgs;

	private MethodParameter returnParamNamedModelAttr;
	private MethodParameter returnParamNonSimpleType;


	@BeforeEach
	public void setup() throws Exception {
		this.request = new ServletWebRequest(new MockHttpServletRequest());
		this.container = new ModelAndViewContainer();
		this.processor = new ModelAttributeMethodProcessor(false);

		Method method = ModelAttributeHandler.class.getDeclaredMethod("modelAttribute",
				TestBean.class, Errors.class, int.class, TestBean.class,
				TestBean.class, TestBean.class, TestBeanWithConstructorArgs.class);

		this.paramNamedValidModelAttr = new SynthesizingMethodParameter(method, 0);
		this.paramErrors = new SynthesizingMethodParameter(method, 1);
		this.paramInt = new SynthesizingMethodParameter(method, 2);
		this.paramModelAttr = new SynthesizingMethodParameter(method, 3);
		this.paramBindingDisabledAttr = new SynthesizingMethodParameter(method, 4);
		this.paramNonSimpleType = new SynthesizingMethodParameter(method, 5);
		this.beanWithConstructorArgs = new SynthesizingMethodParameter(method, 6);

		method = getClass().getDeclaredMethod("annotatedReturnValue");
		this.returnParamNamedModelAttr = new MethodParameter(method, -1);

		method = getClass().getDeclaredMethod("notAnnotatedReturnValue");
		this.returnParamNonSimpleType = new MethodParameter(method, -1);
	}


	@Test
	public void supportedParameters() throws Exception {
		assertThat(this.processor.supportsParameter(this.paramNamedValidModelAttr)).isTrue();
		assertThat(this.processor.supportsParameter(this.paramModelAttr)).isTrue();

		assertThat(this.processor.supportsParameter(this.paramErrors)).isFalse();
		assertThat(this.processor.supportsParameter(this.paramInt)).isFalse();
		assertThat(this.processor.supportsParameter(this.paramNonSimpleType)).isFalse();
	}

	@Test
	public void supportedParametersInDefaultResolutionMode() throws Exception {
		processor = new ModelAttributeMethodProcessor(true);

		// Only non-simple types, even if not annotated
		assertThat(this.processor.supportsParameter(this.paramNamedValidModelAttr)).isTrue();
		assertThat(this.processor.supportsParameter(this.paramErrors)).isTrue();
		assertThat(this.processor.supportsParameter(this.paramModelAttr)).isTrue();
		assertThat(this.processor.supportsParameter(this.paramNonSimpleType)).isTrue();

		assertThat(this.processor.supportsParameter(this.paramInt)).isFalse();
	}

	@Test
	public void supportedReturnTypes() throws Exception {
		processor = new ModelAttributeMethodProcessor(false);
		assertThat(this.processor.supportsReturnType(returnParamNamedModelAttr)).isTrue();
		assertThat(this.processor.supportsReturnType(returnParamNonSimpleType)).isFalse();
	}

	@Test
	public void supportedReturnTypesInDefaultResolutionMode() throws Exception {
		processor = new ModelAttributeMethodProcessor(true);
		assertThat(this.processor.supportsReturnType(returnParamNamedModelAttr)).isTrue();
		assertThat(this.processor.supportsReturnType(returnParamNonSimpleType)).isTrue();
	}

	@Test
	public void bindExceptionRequired() throws Exception {
		assertThat(this.processor.isBindExceptionRequired(null, this.paramNonSimpleType)).isTrue();
		assertThat(this.processor.isBindExceptionRequired(null, this.paramNamedValidModelAttr)).isFalse();
	}

	@Test
	public void resolveArgumentFromModel() throws Exception {
		testGetAttributeFromModel("attrName", this.paramNamedValidModelAttr);
		testGetAttributeFromModel("testBean", this.paramModelAttr);
		testGetAttributeFromModel("testBean", this.paramNonSimpleType);
	}

	@Test
	public void resolveArgumentViaDefaultConstructor() throws Exception {
		WebDataBinder dataBinder = new WebRequestDataBinder(null);
		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(any(), notNull(), eq("attrName"))).willReturn(dataBinder);

		this.processor.resolveArgument(this.paramNamedValidModelAttr, this.container, this.request, factory);
		verify(factory).createBinder(any(), notNull(), eq("attrName"));
	}

	@Test
	public void resolveArgumentValidation() throws Exception {
		String name = "attrName";
		Object target = new TestBean();
		this.container.addAttribute(name, target);

		StubRequestDataBinder dataBinder = new StubRequestDataBinder(target, name);
		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(this.request, target, name)).willReturn(dataBinder);

		this.processor.resolveArgument(this.paramNamedValidModelAttr, this.container, this.request, factory);

		assertThat(dataBinder.isBindInvoked()).isTrue();
		assertThat(dataBinder.isValidateInvoked()).isTrue();
	}

	@Test
	public void resolveArgumentBindingDisabledPreviously() throws Exception {
		String name = "attrName";
		Object target = new TestBean();
		this.container.addAttribute(name, target);

		// Declare binding disabled (e.g. via @ModelAttribute method)
		this.container.setBindingDisabled(name);

		StubRequestDataBinder dataBinder = new StubRequestDataBinder(target, name);
		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(this.request, target, name)).willReturn(dataBinder);

		this.processor.resolveArgument(this.paramNamedValidModelAttr, this.container, this.request, factory);

		assertThat(dataBinder.isBindInvoked()).isFalse();
		assertThat(dataBinder.isValidateInvoked()).isTrue();
	}

	@Test
	public void resolveArgumentBindingDisabled() throws Exception {
		String name = "noBindAttr";
		Object target = new TestBean();
		this.container.addAttribute(name, target);

		StubRequestDataBinder dataBinder = new StubRequestDataBinder(target, name);
		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(this.request, target, name)).willReturn(dataBinder);

		this.processor.resolveArgument(this.paramBindingDisabledAttr, this.container, this.request, factory);

		assertThat(dataBinder.isBindInvoked()).isFalse();
		assertThat(dataBinder.isValidateInvoked()).isTrue();
	}

	@Test
	public void resolveArgumentBindException() throws Exception {
		String name = "testBean";
		Object target = new TestBean();
		this.container.getModel().addAttribute(target);

		StubRequestDataBinder dataBinder = new StubRequestDataBinder(target, name);
		dataBinder.getBindingResult().reject("error");
		WebDataBinderFactory binderFactory = mock(WebDataBinderFactory.class);
		given(binderFactory.createBinder(this.request, target, name)).willReturn(dataBinder);

		assertThatExceptionOfType(BindException.class).isThrownBy(() ->
				this.processor.resolveArgument(this.paramNonSimpleType, this.container, this.request, binderFactory));
		verify(binderFactory).createBinder(this.request, target, name);
	}

	@Test  // SPR-9378
	public void resolveArgumentOrdering() throws Exception {
		String name = "testBean";
		Object testBean = new TestBean(name);
		this.container.addAttribute(name, testBean);
		this.container.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, testBean);

		Object anotherTestBean = new TestBean();
		this.container.addAttribute("anotherTestBean", anotherTestBean);

		StubRequestDataBinder dataBinder = new StubRequestDataBinder(testBean, name);
		WebDataBinderFactory binderFactory = mock(WebDataBinderFactory.class);
		given(binderFactory.createBinder(this.request, testBean, name)).willReturn(dataBinder);

		this.processor.resolveArgument(this.paramModelAttr, this.container, this.request, binderFactory);

		Object[] values = this.container.getModel().values().toArray();
		assertThat(values[1]).as("Resolved attribute should be updated to be last").isSameAs(testBean);
		assertThat(values[2]).as("BindingResult of resolved attr should be last").isSameAs(dataBinder.getBindingResult());
	}

	@Test
	public void handleAnnotatedReturnValue() throws Exception {
		this.processor.handleReturnValue("expected", this.returnParamNamedModelAttr, this.container, this.request);
		assertThat(this.container.getModel().get("modelAttrName")).isEqualTo("expected");
	}

	@Test
	public void handleNotAnnotatedReturnValue() throws Exception {
		TestBean testBean = new TestBean("expected");
		this.processor.handleReturnValue(testBean, this.returnParamNonSimpleType, this.container, this.request);
		assertThat(this.container.getModel().get("testBean")).isSameAs(testBean);
	}

	@Test  // gh-25182
	public void resolveConstructorListArgumentFromCommaSeparatedRequestParameter() throws Exception {
		MockHttpServletRequest mockRequest = new MockHttpServletRequest();
		mockRequest.addParameter("listOfStrings", "1,2");
		ServletWebRequest requestWithParam = new ServletWebRequest(mockRequest);

		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(any(), any(), eq("testBeanWithConstructorArgs")))
				.willAnswer(invocation -> {
					WebRequestDataBinder binder = new WebRequestDataBinder(invocation.getArgument(1));
					// Add conversion service which will convert "1,2" to a list
					binder.setConversionService(new DefaultFormattingConversionService());
					return binder;
				});

		Object resolved = this.processor.resolveArgument(this.beanWithConstructorArgs, this.container, requestWithParam, factory);
		assertThat(resolved).isInstanceOf(TestBeanWithConstructorArgs.class);
		assertThat(((TestBeanWithConstructorArgs) resolved).listOfStrings).containsExactly("1", "2");
	}

	private void testGetAttributeFromModel(String expectedAttrName, MethodParameter param) throws Exception {
		Object target = new TestBean();
		this.container.addAttribute(expectedAttrName, target);

		WebDataBinder dataBinder = new WebRequestDataBinder(target);
		WebDataBinderFactory factory = mock(WebDataBinderFactory.class);
		given(factory.createBinder(this.request, target, expectedAttrName)).willReturn(dataBinder);

		this.processor.resolveArgument(param, this.container, this.request, factory);
		verify(factory).createBinder(this.request, target, expectedAttrName);
	}


	private static class StubRequestDataBinder extends WebRequestDataBinder {

		private boolean bindInvoked;

		private boolean validateInvoked;

		public StubRequestDataBinder(Object target, String objectName) {
			super(target, objectName);
		}

		public boolean isBindInvoked() {
			return bindInvoked;
		}

		public boolean isValidateInvoked() {
			return validateInvoked;
		}

		@Override
		public void bind(WebRequest request) {
			bindInvoked = true;
		}

		@Override
		public void validate() {
			validateInvoked = true;
		}

		@Override
		public void validate(Object... validationHints) {
			validateInvoked = true;
		}
	}


	@Target({METHOD, FIELD, CONSTRUCTOR, PARAMETER})
	@Retention(RUNTIME)
	public @interface Valid {
	}


	@SessionAttributes(types = TestBean.class)
	private static class ModelAttributeHandler {

		@SuppressWarnings("unused")
		public void modelAttribute(
				@ModelAttribute("attrName") @Valid TestBean annotatedAttr,
				Errors errors,
				int intArg,
				@ModelAttribute TestBean defaultNameAttr,
				@ModelAttribute(name="noBindAttr", binding=false) @Valid TestBean noBindAttr,
				TestBean notAnnotatedAttr,
				TestBeanWithConstructorArgs beanWithConstructorArgs) {
		}
	}


	static class TestBeanWithConstructorArgs {

		final List<String> listOfStrings;

		public TestBeanWithConstructorArgs(List<String> listOfStrings) {
			this.listOfStrings = listOfStrings;
		}
	}


	@ModelAttribute("modelAttrName")
	@SuppressWarnings("unused")
	private String annotatedReturnValue() {
		return null;
	}

	@SuppressWarnings("unused")
	private TestBean notAnnotatedReturnValue() {
		return null;
	}

}
