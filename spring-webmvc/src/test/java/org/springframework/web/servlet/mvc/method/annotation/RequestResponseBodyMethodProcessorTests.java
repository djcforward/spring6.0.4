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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.accept.ContentNegotiationManagerFactoryBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.testfixture.servlet.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Test fixture for a {@link RequestResponseBodyMethodProcessor} with
 * actual delegation to {@link HttpMessageConverter} instances. Also see
 * {@link RequestResponseBodyMethodProcessorMockTests}.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 */
@SuppressWarnings("unused")
public class RequestResponseBodyMethodProcessorTests {

	protected static final String NEWLINE_SYSTEM_PROPERTY = System.lineSeparator();


	private ModelAndViewContainer container;

	private MockHttpServletRequest servletRequest;

	private MockHttpServletResponse servletResponse;

	private NativeWebRequest request;

	private ValidatingBinderFactory factory;

	private MethodParameter paramGenericList;
	private MethodParameter paramSimpleBean;
	private MethodParameter paramMultiValueMap;
	private MethodParameter paramString;
	private MethodParameter returnTypeString;


	@BeforeEach
	public void setup() throws Exception {
		container = new ModelAndViewContainer();
		servletRequest = new MockHttpServletRequest();
		servletRequest.setMethod("POST");
		servletResponse = new MockHttpServletResponse();
		request = new ServletWebRequest(servletRequest, servletResponse);
		this.factory = new ValidatingBinderFactory();

		Method method = getClass().getDeclaredMethod("handle",
				List.class, SimpleBean.class, MultiValueMap.class, String.class);
		paramGenericList = new MethodParameter(method, 0);
		paramSimpleBean = new MethodParameter(method, 1);
		paramMultiValueMap = new MethodParameter(method, 2);
		paramString = new MethodParameter(method, 3);
		returnTypeString = new MethodParameter(method, -1);
	}


	@Test
	public void resolveArgumentParameterizedType() throws Exception {
		String content = "[{\"name\" : \"Jad\"}, {\"name\" : \"Robert\"}]";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		@SuppressWarnings("unchecked")
		List<SimpleBean> result = (List<SimpleBean>) processor.resolveArgument(
				paramGenericList, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.get(0).getName()).isEqualTo("Jad");
		assertThat(result.get(1).getName()).isEqualTo("Robert");
	}

	@Test
	public void resolveArgumentRawTypeFromParameterizedType() throws Exception {
		String content = "fruit=apple&vegetable=kale";
		this.servletRequest.setMethod("GET");
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new AllEncompassingFormHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		@SuppressWarnings("unchecked")
		MultiValueMap<String, String> result = (MultiValueMap<String, String>) processor.resolveArgument(
				paramMultiValueMap, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.getFirst("fruit")).isEqualTo("apple");
		assertThat(result.getFirst("vegetable")).isEqualTo("kale");
	}

	@Test
	public void resolveArgumentClassJson() throws Exception {
		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType("application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(
				paramSimpleBean, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("Jad");
	}

	@Test
	public void resolveArgumentClassString() throws Exception {
		String content = "foobarbaz";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType("application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		String result = (String) processor.resolveArgument(
				paramString, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result).isEqualTo("foobarbaz");
	}

	@Test // SPR-9942
	public void resolveArgumentRequiredNoContent() {
		this.servletRequest.setContent(new byte[0]);
		this.servletRequest.setContentType("text/plain");
		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);
		assertThatExceptionOfType(HttpMessageNotReadableException.class).isThrownBy(() ->
				processor.resolveArgument(paramString, container, request, factory));
	}

	@Test  // SPR-12778
	public void resolveArgumentRequiredNoContentDefaultValue() throws Exception {
		this.servletRequest.setContent(new byte[0]);
		this.servletRequest.setContentType("text/plain");
		List<HttpMessageConverter<?>> converters = Collections.singletonList(new StringHttpMessageConverter());
		List<Object> advice = Collections.singletonList(new EmptyRequestBodyAdvice());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters, advice);
		String arg = (String) processor.resolveArgument(paramString, container, request, factory);
		assertThat(arg).isNotNull();
		assertThat(arg).isEqualTo("default value for empty body");
	}

	@Test  // SPR-9964
	public void resolveArgumentTypeVariable() throws Exception {
		Method method = MyParameterizedController.class.getMethod("handleDto", Identifiable.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MySimpleParameterizedController(), method);
		MethodParameter methodParam = handlerMethod.getMethodParameters()[0];

		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(methodParam, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("Jad");
	}

	@Test  // SPR-14470
	public void resolveParameterizedWithTypeVariableArgument() throws Exception {
		Method method = MyParameterizedControllerWithList.class.getMethod("handleDto", List.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MySimpleParameterizedControllerWithList(), method);
		MethodParameter methodParam = handlerMethod.getMethodParameters()[0];

		String content = "[{\"name\" : \"Jad\"}, {\"name\" : \"Robert\"}]";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		@SuppressWarnings("unchecked")
		List<SimpleBean> result = (List<SimpleBean>) processor.resolveArgument(
				methodParam, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.get(0).getName()).isEqualTo("Jad");
		assertThat(result.get(1).getName()).isEqualTo("Robert");
	}

	@Test  // SPR-11225
	public void resolveArgumentTypeVariableWithNonGenericConverter() throws Exception {
		Method method = MyParameterizedController.class.getMethod("handleDto", Identifiable.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MySimpleParameterizedController(), method);
		MethodParameter methodParam = handlerMethod.getMethodParameters()[0];

		String content = "{\"name\" : \"Jad\"}";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		HttpMessageConverter<Object> target = new MappingJackson2HttpMessageConverter();
		HttpMessageConverter<?> proxy = ProxyFactory.getProxy(HttpMessageConverter.class, new SingletonTargetSource(target));
		converters.add(proxy);
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		SimpleBean result = (SimpleBean) processor.resolveArgument(methodParam, container, request, factory);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("Jad");
	}

	@Test  // SPR-9160
	public void handleReturnValueSortByQuality() throws Exception {
		this.servletRequest.addHeader("Accept", "text/plain; q=0.5, application/json");

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		processor.writeWithMessageConverters("Foo", returnTypeString, request);

		assertThat(servletResponse.getHeader("Content-Type")).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
	}

	@Test
	public void handleReturnValueString() throws Exception {
		List<HttpMessageConverter<?>>converters = new ArrayList<>();
		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);
		processor.handleReturnValue("Foo", returnTypeString, container, request);

		assertThat(servletResponse.getHeader("Content-Type")).isEqualTo("text/plain;charset=ISO-8859-1");
		assertThat(servletResponse.getContentAsString()).isEqualTo("Foo");
	}

	@Test  // SPR-13423
	public void handleReturnValueCharSequence() throws Exception {
		List<HttpMessageConverter<?>>converters = new ArrayList<>();
		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new StringHttpMessageConverter());

		Method method = ResponseBodyController.class.getMethod("handleWithCharSequence");
		MethodParameter returnType = new MethodParameter(method, -1);

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);
		processor.handleReturnValue(new StringBuilder("Foo"), returnType, container, request);

		assertThat(servletResponse.getHeader("Content-Type")).isEqualTo("text/plain;charset=ISO-8859-1");
		assertThat(servletResponse.getContentAsString()).isEqualTo("Foo");
	}

	@Test
	public void handleReturnValueStringAcceptCharset() throws Exception {
		this.servletRequest.addHeader("Accept", "text/plain;charset=UTF-8");

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new ByteArrayHttpMessageConverter());
		converters.add(new StringHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		processor.writeWithMessageConverters("Foo", returnTypeString, request);

		assertThat(servletResponse.getHeader("Content-Type")).isEqualTo("text/plain;charset=UTF-8");
	}

	// SPR-12894

	@Test
	public void handleReturnValueImage() throws Exception {
		this.servletRequest.addHeader("Accept", "*/*");

		Method method = getClass().getDeclaredMethod("getImage");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new ResourceHttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		ClassPathResource resource = new ClassPathResource("logo.jpg", getClass());
		processor.writeWithMessageConverters(resource, returnType, this.request);

		assertThat(this.servletResponse.getHeader("Content-Type")).isEqualTo("image/jpeg");
	}

	@Test // gh-26212
	public void handleReturnValueWithObjectMapperByTypeRegistration() throws Exception {
		MediaType halFormsMediaType = MediaType.parseMediaType("application/prs.hal-forms+json");
		MediaType halMediaType = MediaType.parseMediaType("application/hal+json");

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);

		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
		converter.registerObjectMappersForType(SimpleBean.class, map -> map.put(halMediaType, objectMapper));

		this.servletRequest.addHeader("Accept", halFormsMediaType + "," + halMediaType);

		SimpleBean simpleBean = new SimpleBean();
		simpleBean.setId(12L);
		simpleBean.setName("Jason");

		RequestResponseBodyMethodProcessor processor =
				new RequestResponseBodyMethodProcessor(Collections.singletonList(converter));
		MethodParameter returnType = new MethodParameter(getClass().getDeclaredMethod("getSimpleBean"), -1);
		processor.writeWithMessageConverters(simpleBean, returnType, this.request);

		assertThat(this.servletResponse.getHeader("Content-Type")).isEqualTo(halMediaType.toString());
		assertThat(this.servletResponse.getContentAsString()).isEqualTo(
				"{" + NEWLINE_SYSTEM_PROPERTY +
				"  \"id\" : 12," + NEWLINE_SYSTEM_PROPERTY +
				"  \"name\" : \"Jason\"" + NEWLINE_SYSTEM_PROPERTY +
				"}");
	}

	@Test
	void problemDetailDefaultMediaType() throws Exception {
		testProblemDetailMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
	}

	@Test
	void problemDetailWhenJsonRequested() throws Exception {
		this.servletRequest.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
		testProblemDetailMediaType(MediaType.APPLICATION_JSON_VALUE);
	}

	@Test
	void problemDetailWhenNoMatchingMediaTypeRequested() throws Exception {
		this.servletRequest.addHeader("Accept", MediaType.APPLICATION_PDF_VALUE);
		testProblemDetailMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
	}

	private void testProblemDetailMediaType(String expectedContentType) throws Exception {

		ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

		this.servletRequest.setRequestURI("/path");

		RequestResponseBodyMethodProcessor processor =
				new RequestResponseBodyMethodProcessor(
						Collections.singletonList(new MappingJackson2HttpMessageConverter()));

		MethodParameter returnType =
				new MethodParameter(getClass().getDeclaredMethod("handleAndReturnProblemDetail"), -1);

		processor.handleReturnValue(problemDetail, returnType, this.container, this.request);

		assertThat(this.servletResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(this.servletResponse.getContentType()).isEqualTo(expectedContentType);
		assertThat(this.servletResponse.getContentAsString()).isEqualTo(
				"{\"type\":\"about:blank\"," +
						"\"title\":\"Bad Request\"," +
						"\"status\":400," +
						"\"instance\":\"/path\"}");
	}

	@Test // SPR-13135
	public void handleReturnValueWithInvalidReturnType() throws Exception {
		Method method = getClass().getDeclaredMethod("handleAndReturnOutputStream");
		MethodParameter returnType = new MethodParameter(method, -1);
		assertThatIllegalArgumentException().isThrownBy(() -> {
				RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(new ArrayList<>());
				processor.writeWithMessageConverters(new ByteArrayOutputStream(), returnType, this.request);
		});
	}

	@Test
	public void addContentDispositionHeader() throws Exception {
		ContentNegotiationManagerFactoryBean factory = new ContentNegotiationManagerFactoryBean();
		factory.addMediaType("pdf", new MediaType("application", "pdf"));
		factory.afterPropertiesSet();

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				Collections.singletonList(new StringHttpMessageConverter()),
				factory.getObject());

		assertContentDisposition(processor, false, "/hello.json", "safe extension");
		assertContentDisposition(processor, false, "/hello.pdf", "registered extension");
		assertContentDisposition(processor, true, "/hello.dataless", "unknown extension");

		// path parameters
		assertContentDisposition(processor, false, "/hello.json;a=b", "path param shouldn't cause issue");
		assertContentDisposition(processor, true, "/hello.json;a=b;setup.dataless", "unknown ext in path params");
		assertContentDisposition(processor, true, "/hello.dataless;a=b;setup.json", "unknown ext in filename");
		assertContentDisposition(processor, false, "/hello.json;a=b;setup.json", "safe extensions");
		assertContentDisposition(processor, true, "/hello.json;jsessionid=foo.bar", "jsessionid shouldn't cause issue");

		// encoded dot
		assertContentDisposition(processor, true, "/hello%2Edataless;a=b;setup.json", "encoded dot in filename");
		assertContentDisposition(processor, true, "/hello.json;a=b;setup%2Edataless", "encoded dot in path params");
		assertContentDisposition(processor, true, "/hello.dataless%3Bsetup.bat", "encoded dot in path params");

		this.servletRequest.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, "/hello.bat");
		assertContentDisposition(processor, true, "/bonjour", "forwarded URL");
		this.servletRequest.removeAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
	}

	@Test
	public void addContentDispositionHeaderToErrorResponse() throws Exception {
		ContentNegotiationManagerFactoryBean factory = new ContentNegotiationManagerFactoryBean();
		factory.addMediaType("pdf", new MediaType("application", "pdf"));
		factory.afterPropertiesSet();

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				Collections.singletonList(new StringHttpMessageConverter()),
				factory.getObject());

		this.servletRequest.setRequestURI("/hello.dataless");
		this.servletResponse.setStatus(400);

		processor.handleReturnValue("body", this.returnTypeString, this.container, this.request);

		String header = servletResponse.getHeader("Content-Disposition");
		assertThat(header).isEqualTo("inline;filename=f.txt");
	}

	@Test
	public void supportsReturnTypeResponseBodyOnType() throws Exception {
		Method method = ResponseBodyController.class.getMethod("handle");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertThat(processor.supportsReturnType(returnType)).as("Failed to recognize type-level @ResponseBody").isTrue();
	}

	@Test
	public void supportsReturnTypeRestController() throws Exception {
		Method method = TestRestController.class.getMethod("handle");
		MethodParameter returnType = new MethodParameter(method, -1);

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new StringHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertThat(processor.supportsReturnType(returnType)).as("Failed to recognize type-level @RestController").isTrue();
	}

	@Test
	public void jacksonJsonViewWithResponseBodyAndJsonMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseBody");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseBody();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("\"withView1\":\"with\"")).isFalse();
		assertThat(content.contains("\"withView2\":\"with\"")).isTrue();
		assertThat(content.contains("\"withoutView\":\"without\"")).isFalse();
	}

	@Test
	public void jacksonJsonViewWithResponseEntityAndJsonMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseEntity");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseEntity();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("\"withView1\":\"with\"")).isFalse();
		assertThat(content.contains("\"withView2\":\"with\"")).isTrue();
		assertThat(content.contains("\"withoutView\":\"without\"")).isFalse();
	}

	@Test  // SPR-12149
	public void jacksonJsonViewWithResponseBodyAndXmlMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseBody");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseBody();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("<withView1>with</withView1>")).isFalse();
		assertThat(content.contains("<withView2>with</withView2>")).isTrue();
		assertThat(content.contains("<withoutView>without</withoutView>")).isFalse();
	}

	@Test  // SPR-12149
	public void jacksonJsonViewWithResponseEntityAndXmlMessageConverter() throws Exception {
		Method method = JacksonController.class.getMethod("handleResponseEntity");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewResponseBodyAdvice()));

		Object returnValue = new JacksonController().handleResponseEntity();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("<withView1>with</withView1>")).isFalse();
		assertThat(content.contains("<withView2>with</withView2>")).isTrue();
		assertThat(content.contains("<withoutView>without</withoutView>")).isFalse();
	}

	@Test  // SPR-12501
	public void resolveArgumentWithJacksonJsonView() throws Exception {
		String content = "{\"withView1\" : \"with\", \"withView2\" : \"with\", \"withoutView\" : \"without\"}";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = JacksonController.class.getMethod("handleRequestBody", JacksonViewBean.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		JacksonViewBean result = (JacksonViewBean)
				processor.resolveArgument(methodParameter, this.container, this.request, this.factory);

		assertThat(result).isNotNull();
		assertThat(result.getWithView1()).isEqualTo("with");
		assertThat(result.getWithView2()).isNull();
		assertThat(result.getWithoutView()).isNull();
	}

	@Test  // SPR-12501
	public void resolveHttpEntityArgumentWithJacksonJsonView() throws Exception {
		String content = "{\"withView1\" : \"with\", \"withView2\" : \"with\", \"withoutView\" : \"without\"}";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = JacksonController.class.getMethod("handleHttpEntity", HttpEntity.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		HttpEntity<JacksonViewBean> result = (HttpEntity<JacksonViewBean>)
				processor.resolveArgument( methodParameter, this.container, this.request, this.factory);

		assertThat(result).isNotNull();
		assertThat(result.getBody()).isNotNull();
		assertThat(result.getBody().getWithView1()).isEqualTo("with");
		assertThat(result.getBody().getWithView2()).isNull();
		assertThat(result.getBody().getWithoutView()).isNull();
	}

	@Test  // SPR-12501
	public void resolveArgumentWithJacksonJsonViewAndXmlMessageConverter() throws Exception {
		String content = "<root>" +
				"<withView1>with</withView1>" +
				"<withView2>with</withView2>" +
				"<withoutView>without</withoutView></root>";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_XML_VALUE);

		Method method = JacksonController.class.getMethod("handleRequestBody", JacksonViewBean.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		JacksonViewBean result = (JacksonViewBean)
				processor.resolveArgument(methodParameter, this.container, this.request, this.factory);

		assertThat(result).isNotNull();
		assertThat(result.getWithView1()).isEqualTo("with");
		assertThat(result.getWithView2()).isNull();
		assertThat(result.getWithoutView()).isNull();
	}

	@Test  // SPR-12501
	public void resolveHttpEntityArgumentWithJacksonJsonViewAndXmlMessageConverter() throws Exception {
		String content = "<root>" +
				"<withView1>with</withView1>" +
				"<withView2>with</withView2>" +
				"<withoutView>without</withoutView></root>";
		this.servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_XML_VALUE);

		Method method = JacksonController.class.getMethod("handleHttpEntity", HttpEntity.class);
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2XmlHttpMessageConverter());

		HttpEntityMethodProcessor processor = new HttpEntityMethodProcessor(
				converters, null, Collections.singletonList(new JsonViewRequestBodyAdvice()));

		@SuppressWarnings("unchecked")
		HttpEntity<JacksonViewBean> result = (HttpEntity<JacksonViewBean>)
				processor.resolveArgument(methodParameter, this.container, this.request, this.factory);

		assertThat(result).isNotNull();
		assertThat(result.getBody()).isNotNull();
		assertThat(result.getBody().getWithView1()).isEqualTo("with");
		assertThat(result.getBody().getWithView2()).isNull();
		assertThat(result.getBody().getWithoutView()).isNull();
	}

	@Test  // SPR-12811
	public void jacksonTypeInfoList() throws Exception {
		Method method = JacksonController.class.getMethod("handleTypeInfoList");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleTypeInfoList();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("\"type\":\"foo\"")).isTrue();
		assertThat(content.contains("\"type\":\"bar\"")).isTrue();
	}

	@Test  // SPR-13318
	public void jacksonSubType() throws Exception {
		Method method = JacksonController.class.getMethod("handleSubType");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleSubType();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("\"id\":123")).isTrue();
		assertThat(content.contains("\"name\":\"foo\"")).isTrue();
	}

	@Test  // SPR-13318
	public void jacksonSubTypeList() throws Exception {
		Method method = JacksonController.class.getMethod("handleSubTypeList");
		HandlerMethod handlerMethod = new HandlerMethod(new JacksonController(), method);
		MethodParameter methodReturnType = handlerMethod.getReturnType();

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());
		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		Object returnValue = new JacksonController().handleSubTypeList();
		processor.handleReturnValue(returnValue, methodReturnType, this.container, this.request);

		String content = this.servletResponse.getContentAsString();
		assertThat(content.contains("\"id\":123")).isTrue();
		assertThat(content.contains("\"name\":\"foo\"")).isTrue();
		assertThat(content.contains("\"id\":456")).isTrue();
		assertThat(content.contains("\"name\":\"bar\"")).isTrue();
	}

	@Test  // SPR-14520
	public void resolveArgumentTypeVariableWithGenericInterface() throws Exception {
		this.servletRequest.setContent("\"foo\"".getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = MyControllerImplementingInterface.class.getMethod("handle", Object.class);
		HandlerMethod handlerMethod = new HandlerMethod(new MyControllerImplementingInterface(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertThat(processor.supportsParameter(methodParameter)).isTrue();
		String value = (String) processor.readWithMessageConverters(
				this.request, methodParameter, methodParameter.getGenericParameterType());
		assertThat(value).isEqualTo("foo");
	}

	@Test  // gh-24127
	public void resolveArgumentTypeVariableWithGenericInterfaceAndSubclass() throws Exception {
		this.servletRequest.setContent("\"foo\"".getBytes(StandardCharsets.UTF_8));
		this.servletRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Method method = SubControllerImplementingInterface.class.getMethod("handle", Object.class);
		HandlerMethod handlerMethod = new HandlerMethod(new SubControllerImplementingInterface(), method);
		MethodParameter methodParameter = handlerMethod.getMethodParameters()[0];

		List<HttpMessageConverter<?>> converters = new ArrayList<>();
		converters.add(new MappingJackson2HttpMessageConverter());

		RequestResponseBodyMethodProcessor processor = new RequestResponseBodyMethodProcessor(converters);

		assertThat(processor.supportsParameter(methodParameter)).isTrue();
		String value = (String) processor.readWithMessageConverters(
				this.request, methodParameter, methodParameter.getGenericParameterType());
		assertThat(value).isEqualTo("foo");
	}

	private void assertContentDisposition(RequestResponseBodyMethodProcessor processor,
			boolean expectContentDisposition, String requestURI, String comment) throws Exception {

		this.servletRequest.setRequestURI(requestURI);
		processor.handleReturnValue("body", this.returnTypeString, this.container, this.request);

		String header = servletResponse.getHeader("Content-Disposition");
		if (expectContentDisposition) {
			assertThat(header)
					.as("Expected 'Content-Disposition' header. Use case: '" + comment + "'")
					.isEqualTo("inline;filename=f.txt");
		}
		else {
			assertThat(header)
					.as("Did not expect 'Content-Disposition' header. Use case: '" + comment + "'")
					.isNull();
		}

		this.servletRequest = new MockHttpServletRequest();
		this.servletResponse = new MockHttpServletResponse();
		this.request = new ServletWebRequest(servletRequest, servletResponse);
	}


	@SuppressWarnings("ConstantConditions")
	String handle(
			@RequestBody List<SimpleBean> list,
			@RequestBody SimpleBean simpleBean,
			@RequestBody MultiValueMap<String, String> multiValueMap,
			@RequestBody String string) {

		return null;
	}

	@SuppressWarnings("ConstantConditions")
	Resource getImage() {
		return null;
	}

	@SuppressWarnings("ConstantConditions")
	ProblemDetail handleAndReturnProblemDetail() {
		return null;
	}

	@SuppressWarnings("ConstantConditions")
	@RequestMapping
	OutputStream handleAndReturnOutputStream() {
		return null;
	}

	@SuppressWarnings("ConstantConditions")
	SimpleBean getSimpleBean() {
		return null;
	}


	private static abstract class MyParameterizedController<DTO extends Identifiable> {

		@SuppressWarnings("unused")
		public void handleDto(@RequestBody DTO dto) {}
	}


	private static class MySimpleParameterizedController extends MyParameterizedController<SimpleBean> {
	}


	private interface Identifiable extends Serializable {

		Long getId();

		void setId(Long id);
	}


	@SuppressWarnings("unused")
	private static abstract class MyParameterizedControllerWithList<DTO extends Identifiable> {

		public void handleDto(@RequestBody List<DTO> dto) {
		}
	}


	@SuppressWarnings("unused")
	private static class MySimpleParameterizedControllerWithList extends MyParameterizedControllerWithList<SimpleBean> {
	}


	@SuppressWarnings({"serial", "NotNullFieldNotInitialized"})
	private static class SimpleBean implements Identifiable {

		private Long id;

		private String name;

		@Override
		public Long getId() {
			return id;
		}

		@Override
		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}


	private static final class ValidatingBinderFactory implements WebDataBinderFactory {

		@Override
		public WebDataBinder createBinder(NativeWebRequest request, @Nullable Object target, String objectName) {
			LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
			validator.afterPropertiesSet();
			WebDataBinder dataBinder = new WebDataBinder(target, objectName);
			dataBinder.setValidator(validator);
			return dataBinder;
		}
	}


	@ResponseBody
	private static class ResponseBodyController {

		@RequestMapping
		public String handle() {
			return "hello";
		}

		@SuppressWarnings("ConstantConditions")
		@RequestMapping
		public CharSequence handleWithCharSequence() {
			return null;
		}
	}


	@RestController
	private static class TestRestController {

		@RequestMapping
		public String handle() {
			return "hello";
		}
	}


	private interface MyJacksonView1 {}

	private interface MyJacksonView2 {}


	@SuppressWarnings("NotNullFieldNotInitialized")
	private static class JacksonViewBean {

		@JsonView(MyJacksonView1.class)
		private String withView1;

		@JsonView(MyJacksonView2.class)
		private String withView2;

		private String withoutView;

		public String getWithView1() {
			return withView1;
		}

		public void setWithView1(String withView1) {
			this.withView1 = withView1;
		}

		@Nullable
		public String getWithView2() {
			return withView2;
		}

		public void setWithView2(String withView2) {
			this.withView2 = withView2;
		}

		@Nullable
		public String getWithoutView() {
			return withoutView;
		}

		public void setWithoutView(String withoutView) {
			this.withoutView = withoutView;
		}
	}


	@SuppressWarnings("NotNullFieldNotInitialized")
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
	public static class ParentClass {

		private String parentProperty;

		public ParentClass() {
		}

		public ParentClass(String parentProperty) {
			this.parentProperty = parentProperty;
		}

		public String getParentProperty() {
			return parentProperty;
		}

		public void setParentProperty(String parentProperty) {
			this.parentProperty = parentProperty;
		}
	}


	@JsonTypeName("foo")
	public static class Foo extends ParentClass {

		public Foo() {
		}

		public Foo(String parentProperty) {
			super(parentProperty);
		}
	}


	@JsonTypeName("bar")
	public static class Bar extends ParentClass {

		public Bar() {
		}

		public Bar(String parentProperty) {
			super(parentProperty);
		}
	}


	private static class BaseController<T> {

		@RequestMapping
		@ResponseBody
		@SuppressWarnings("unchecked")
		public List<T> handleTypeInfoList() {
			List<T> list = new ArrayList<>();
			list.add((T) new Foo("foo"));
			list.add((T) new Bar("bar"));
			return list;
		}
	}


	private static class JacksonController extends BaseController<ParentClass> {

		@RequestMapping
		@ResponseBody
		@JsonView(MyJacksonView2.class)
		public JacksonViewBean handleResponseBody() {
			JacksonViewBean bean = new JacksonViewBean();
			bean.setWithView1("with");
			bean.setWithView2("with");
			bean.setWithoutView("without");
			return bean;
		}

		@RequestMapping
		@JsonView(MyJacksonView2.class)
		public ResponseEntity<JacksonViewBean> handleResponseEntity() {
			JacksonViewBean bean = new JacksonViewBean();
			bean.setWithView1("with");
			bean.setWithView2("with");
			bean.setWithoutView("without");
			ModelAndView mav = new ModelAndView(new MappingJackson2JsonView());
			mav.addObject("bean", bean);
			return new ResponseEntity<>(bean, HttpStatus.OK);
		}

		@RequestMapping
		@ResponseBody
		public JacksonViewBean handleRequestBody(@JsonView(MyJacksonView1.class) @RequestBody JacksonViewBean bean) {
			return bean;
		}

		@SuppressWarnings("ConstantConditions")
		@RequestMapping
		@ResponseBody
		public JacksonViewBean handleHttpEntity(@JsonView(MyJacksonView1.class) HttpEntity<JacksonViewBean> entity) {
			return entity.getBody();
		}

		@RequestMapping
		@ResponseBody
		public Identifiable handleSubType() {
			SimpleBean foo = new SimpleBean();
			foo.setId(123L);
			foo.setName("foo");
			return foo;
		}

		@RequestMapping
		@ResponseBody
		public List<Identifiable> handleSubTypeList() {
			SimpleBean foo = new SimpleBean();
			foo.setId(123L);
			foo.setName("foo");
			SimpleBean bar = new SimpleBean();
			bar.setId(456L);
			bar.setName("bar");
			return Arrays.asList(foo, bar);
		}

		@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
		@ResponseBody
		public String defaultCharset() {
			return "foo";
		}
	}


	private static class EmptyRequestBodyAdvice implements RequestBodyAdvice {

		@Override
		public boolean supports(MethodParameter methodParameter, Type targetType,
				Class<? extends HttpMessageConverter<?>> converterType) {

			return StringHttpMessageConverter.class.equals(converterType);
		}

		@Override
		public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return inputMessage;
		}

		@Override
		public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return body;
		}

		@Override
		public Object handleEmptyBody(@Nullable Object body, HttpInputMessage inputMessage, MethodParameter parameter,
				Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

			return "default value for empty body";
		}
	}


	interface MappingInterface<A> {

		default A handle(@RequestBody A arg) {
			return arg;
		}
	}


	static class MyControllerImplementingInterface implements MappingInterface<String> {
	}


	static class SubControllerImplementingInterface extends MyControllerImplementingInterface {

		@Override
		public String handle(String arg) {
			return arg;
		}
	}

}
