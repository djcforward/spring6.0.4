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

package org.springframework.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import org.springframework.beans.testfixture.beans.TestBean;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingMatrixVariableException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerErrorException;
import org.springframework.web.server.UnsatisfiedRequestParameterException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import org.springframework.web.testfixture.method.ResolvableMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests that verify the HTTP response details exposed by exceptions in the
 * {@link ErrorResponse} hierarchy.
 *
 * @author Rossen Stoyanchev
 */
public class ErrorResponseExceptionTests {

	private final MethodParameter methodParameter =
			new MethodParameter(ResolvableMethod.on(getClass()).resolveMethod("handle"), 0);


	@Test
	void httpMediaTypeNotSupportedException() {

		List<MediaType> mediaTypes =
				Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_CBOR);

		HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
				MediaType.APPLICATION_XML, mediaTypes, HttpMethod.PATCH, "Custom message");

		assertStatus(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertDetail(ex, "Content-Type 'application/xml' is not supported.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getContentType(), ex.getSupportedMediaTypes()});

		HttpHeaders headers = ex.getHeaders();
		assertThat(headers.getAccept()).isEqualTo(mediaTypes);
		assertThat(headers.getAcceptPatch()).isEqualTo(mediaTypes);
	}

	@Test
	void httpMediaTypeNotSupportedExceptionWithParseError() {

		ErrorResponse ex = new HttpMediaTypeNotSupportedException(
				"Could not parse Accept header: Invalid mime type \"foo\": does not contain '/'");

		assertStatus(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertDetail(ex, "Could not parse Content-Type.");
		assertDetailMessageCode(ex, "parseError", null);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void httpMediaTypeNotAcceptableException() {

		List<MediaType> mediaTypes = Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_CBOR);
		HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException(mediaTypes);

		assertStatus(ex, HttpStatus.NOT_ACCEPTABLE);
		assertDetail(ex, "Acceptable representations: [application/json, application/cbor].");
		assertDetailMessageCode(ex, null, new Object[] {ex.getSupportedMediaTypes()});

		assertThat(ex.getHeaders()).hasSize(1);
		assertThat(ex.getHeaders().getAccept()).isEqualTo(mediaTypes);
	}

	@Test
	void httpMediaTypeNotAcceptableExceptionWithParseError() {

		ErrorResponse ex = new HttpMediaTypeNotAcceptableException(
				"Could not parse Accept header: Invalid mime type \"foo\": does not contain '/'");

		assertStatus(ex, HttpStatus.NOT_ACCEPTABLE);
		assertDetail(ex, "Could not parse Accept header.");
		assertDetailMessageCode(ex, "parseError", null);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void asyncRequestTimeoutException() {

		ErrorResponse ex = new AsyncRequestTimeoutException();
		assertDetailMessageCode(ex, null, null);

		assertStatus(ex, HttpStatus.SERVICE_UNAVAILABLE);
		assertDetail(ex, null);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void httpRequestMethodNotSupportedException() {

		HttpRequestMethodNotSupportedException ex =
				new HttpRequestMethodNotSupportedException("PUT", Arrays.asList("GET", "POST"));

		assertStatus(ex, HttpStatus.METHOD_NOT_ALLOWED);
		assertDetail(ex, "Method 'PUT' is not supported.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getMethod(), ex.getSupportedHttpMethods()});

		assertThat(ex.getHeaders()).hasSize(1);
		assertThat(ex.getHeaders().getAllow()).containsExactly(HttpMethod.GET, HttpMethod.POST);
	}

	@Test
	void missingRequestHeaderException() {

		MissingRequestHeaderException ex = new MissingRequestHeaderException("Authorization", this.methodParameter);

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required header 'Authorization' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getHeaderName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingServletRequestParameterException() {

		MissingServletRequestParameterException ex = new MissingServletRequestParameterException("query", "String");

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required parameter 'query' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getParameterName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingMatrixVariableException() {

		MissingMatrixVariableException ex = new MissingMatrixVariableException("region", this.methodParameter);


		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required path parameter 'region' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getVariableName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingPathVariableException() {

		MissingPathVariableException ex = new MissingPathVariableException("id", this.methodParameter);

		assertStatus(ex, HttpStatus.INTERNAL_SERVER_ERROR);
		assertDetail(ex, "Required path variable 'id' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getVariableName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingRequestCookieException() {

		MissingRequestCookieException ex = new MissingRequestCookieException("oreo", this.methodParameter);

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required cookie 'oreo' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getCookieName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void unsatisfiedServletRequestParameterException() {

		UnsatisfiedServletRequestParameterException ex = new UnsatisfiedServletRequestParameterException(
				new String[] { "foo=bar", "bar=baz" }, Collections.singletonMap("q", new String[] {"1"}));

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Invalid request parameters.");
		assertDetailMessageCode(ex, null, new Object[] {List.of("\"foo=bar, bar=baz\"")});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingServletRequestPartException() {

		MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required part 'file' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getRequestPartName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void methodArgumentNotValidException() {

		MessageSourceTestHelper messageSourceHelper = new MessageSourceTestHelper(MethodArgumentNotValidException.class);
		BindingResult bindingResult = messageSourceHelper.initBindingResult();

		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(this.methodParameter, bindingResult);

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Invalid request content.");
		messageSourceHelper.assertDetailMessage(ex);
		messageSourceHelper.assertErrorMessages(ex::resolveErrorMessages);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void unsupportedMediaTypeStatusException() {

		List<MediaType> mediaTypes =
				Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_CBOR);

		UnsupportedMediaTypeStatusException ex = new UnsupportedMediaTypeStatusException(
				MediaType.APPLICATION_XML, mediaTypes, HttpMethod.PATCH);

		assertStatus(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertDetail(ex, "Content-Type 'application/xml' is not supported.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getContentType(), ex.getSupportedMediaTypes()});

		HttpHeaders headers = ex.getHeaders();
		assertThat(headers.getAccept()).isEqualTo(mediaTypes);
		assertThat(headers.getAcceptPatch()).isEqualTo(mediaTypes);
	}

	@Test
	void unsupportedMediaTypeStatusExceptionWithParseError() {

		ErrorResponse ex = new UnsupportedMediaTypeStatusException(
				"Could not parse Accept header: Invalid mime type \"foo\": does not contain '/'");

		assertStatus(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertDetail(ex, "Could not parse Content-Type.");
		assertDetailMessageCode(ex, "parseError", null);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void notAcceptableStatusException() {

		List<MediaType> mediaTypes = Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_CBOR);
		NotAcceptableStatusException ex = new NotAcceptableStatusException(mediaTypes);

		assertStatus(ex, HttpStatus.NOT_ACCEPTABLE);
		assertDetail(ex, "Acceptable representations: [application/json, application/cbor].");
		assertDetailMessageCode(ex, null, new Object[] {ex.getSupportedMediaTypes()});

		assertThat(ex.getHeaders()).hasSize(1);
		assertThat(ex.getHeaders().getAccept()).isEqualTo(mediaTypes);
	}

	@Test
	void notAcceptableStatusExceptionWithParseError() {

		ErrorResponse ex = new NotAcceptableStatusException(
				"Could not parse Accept header: Invalid mime type \"foo\": does not contain '/'");

		assertStatus(ex, HttpStatus.NOT_ACCEPTABLE);
		assertDetail(ex, "Could not parse Accept header.");
		assertDetailMessageCode(ex, "parseError", null);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void serverErrorException() {

		ServerErrorException ex = new ServerErrorException("Failure", null);

		assertStatus(ex, HttpStatus.INTERNAL_SERVER_ERROR);
		assertDetail(ex, "Failure");
		assertDetailMessageCode(ex, null, new Object[] {ex.getReason()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void missingRequestValueException() {

		MissingRequestValueException ex =
				new MissingRequestValueException("foo", String.class, "header", this.methodParameter);

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Required header 'foo' is not present.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getLabel(), ex.getName()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void unsatisfiedRequestParameterException() {

		UnsatisfiedRequestParameterException ex =
				new UnsatisfiedRequestParameterException(
						Arrays.asList("foo=bar", "bar=baz"),
						new LinkedMultiValueMap<>(Collections.singletonMap("q", Arrays.asList("1", "2"))));

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Invalid request parameters.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getConditions()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void webExchangeBindException() {

		MessageSourceTestHelper messageSourceHelper = new MessageSourceTestHelper(WebExchangeBindException.class);
		BindingResult bindingResult = messageSourceHelper.initBindingResult();

		WebExchangeBindException ex = new WebExchangeBindException(this.methodParameter, bindingResult);

		assertStatus(ex, HttpStatus.BAD_REQUEST);
		assertDetail(ex, "Invalid request content.");
		messageSourceHelper.assertDetailMessage(ex);
		messageSourceHelper.assertErrorMessages(ex::resolveErrorMessages);

		assertThat(ex.getHeaders()).isEmpty();
	}

	@Test
	void methodNotAllowedException() {

		List<HttpMethod> supportedMethods = Arrays.asList(HttpMethod.GET, HttpMethod.POST);
		MethodNotAllowedException ex = new MethodNotAllowedException(HttpMethod.PUT, supportedMethods);

		assertStatus(ex, HttpStatus.METHOD_NOT_ALLOWED);
		assertDetail(ex, "Supported methods: [GET, POST]");
		assertDetailMessageCode(ex, null, new Object[] {ex.getHttpMethod(), supportedMethods});

		assertThat(ex.getHeaders()).hasSize(1);
		assertThat(ex.getHeaders().getAllow()).containsExactly(HttpMethod.GET, HttpMethod.POST);
	}

	@Test
	void methodNotAllowedExceptionWithoutSupportedMethods() {

		MethodNotAllowedException ex = new MethodNotAllowedException(HttpMethod.PUT, Collections.emptyList());

		assertStatus(ex, HttpStatus.METHOD_NOT_ALLOWED);
		assertDetail(ex, "Request method 'PUT' is not supported.");
		assertDetailMessageCode(ex, null, new Object[] {ex.getHttpMethod(), Collections.emptyList()});

		assertThat(ex.getHeaders()).isEmpty();
	}

	private void assertStatus(ErrorResponse ex, HttpStatus status) {
		ProblemDetail body = ex.getBody();
		assertThat(ex.getStatusCode()).isEqualTo(status);
		assertThat(body.getStatus()).isEqualTo(status.value());
		assertThat(body.getTitle()).isEqualTo(status.getReasonPhrase());
	}

	private void assertDetail(ErrorResponse ex, @Nullable String detail) {
		if (detail != null) {
			assertThat(ex.getBody().getDetail()).isEqualTo(detail);
		}
		else {
			assertThat(ex.getBody().getDetail()).isNull();
		}
	}

	private void assertDetailMessageCode(
			ErrorResponse ex, @Nullable String suffix, @Nullable Object[] arguments) {

		assertThat(ex.getDetailMessageCode())
				.isEqualTo(ErrorResponse.getDefaultDetailMessageCode(((Exception) ex).getClass(), suffix));

		if (arguments != null) {
			assertThat(ex.getDetailMessageArguments()).containsExactlyElementsOf(Arrays.asList(arguments));
		}
		else {
			assertThat(ex.getDetailMessageArguments()).isNull();
		}
	}


	@SuppressWarnings("unused")
	private void handle(String arg) {}


	private static class MessageSourceTestHelper {

		private final String code;

		public MessageSourceTestHelper(Class<? extends ErrorResponse> exceptionType) {
			this.code = "problemDetail." + exceptionType.getName();
		}

		public BindingResult initBindingResult() {
			BindingResult bindingResult = new BindException(new TestBean(), "myBean");
			bindingResult.reject("bean.invalid.A", "Invalid bean message");
			bindingResult.reject("bean.invalid.B");
			bindingResult.rejectValue("name", "name.required", "Name is required");
			bindingResult.rejectValue("age", "age.min");
			return bindingResult;
		}

		private void assertDetailMessage(ErrorResponse ex) {

			StaticMessageSource messageSource = initMessageSource();

			String message = messageSource.getMessage(
					ex.getDetailMessageCode(), ex.getDetailMessageArguments(), Locale.UK);

			assertThat(message).isEqualTo("" +
					"Failures ['Invalid bean message', 'bean.invalid.B']. " +
					"nested failures: [name: 'Name is required', age: 'age.min']");

			message = messageSource.getMessage(
					ex.getDetailMessageCode(), ex.getDetailMessageArguments(messageSource, Locale.UK), Locale.UK);

			assertThat(message).isEqualTo("" +
					"Failures ['Bean A message', 'Bean B message']. " +
					"nested failures: [name: 'Required name message', age: 'Minimum age message']");
		}

		private void assertErrorMessages(BiFunction<MessageSource, Locale, Map<ObjectError, String>> expectedMessages) {
			StaticMessageSource messageSource = initMessageSource();
			Map<ObjectError, String> map = expectedMessages.apply(messageSource, Locale.UK);

			assertThat(map).hasSize(4).containsValues(
					"'Bean A message'", "'Bean B message'", "name: 'Required name message'", "age: 'Minimum age message'");
		}

		private StaticMessageSource initMessageSource() {
			StaticMessageSource messageSource = new StaticMessageSource();
			messageSource.addMessage(this.code, Locale.UK, "Failures {0}. nested failures: {1}");
			messageSource.addMessage("bean.invalid.A", Locale.UK, "Bean A message");
			messageSource.addMessage("bean.invalid.B", Locale.UK, "Bean B message");
			messageSource.addMessage("name.required", Locale.UK, "Required name message");
			messageSource.addMessage("age.min", Locale.UK, "Minimum age message");
			return messageSource;
		}
	}

}
