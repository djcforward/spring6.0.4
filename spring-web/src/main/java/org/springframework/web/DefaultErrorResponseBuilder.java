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

import java.net.URI;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;


/**
 * Default implementation of {@link ErrorResponse.Builder}.
 *
 * @author Rossen Stoyanchev
 * @since 6.0
 */
final class DefaultErrorResponseBuilder implements ErrorResponse.Builder {

	private final Throwable exception;

	private final HttpStatusCode statusCode;

	@Nullable
	private HttpHeaders headers;

	private final ProblemDetail problemDetail;

	private String detailMessageCode;

	@Nullable
	private Object[] detailMessageArguments;

	private String titleMessageCode;


	DefaultErrorResponseBuilder(Throwable ex, HttpStatusCode statusCode, String detail) {
		Assert.notNull(ex, "Throwable is required");
		Assert.notNull(statusCode, "HttpStatusCode is required");
		Assert.notNull(detail, "`detail` is required");
		this.exception = ex;
		this.statusCode = statusCode;
		this.problemDetail = ProblemDetail.forStatusAndDetail(statusCode, detail);
		this.detailMessageCode = ErrorResponse.getDefaultDetailMessageCode(ex.getClass(), null);
		this.titleMessageCode = ErrorResponse.getDefaultTitleMessageCode(ex.getClass());
	}


	@Override
	public ErrorResponse.Builder header(String headerName, String... headerValues) {
		this.headers = (this.headers != null ? this.headers : new HttpHeaders());
		for (String headerValue : headerValues) {
			this.headers.add(headerName, headerValue);
		}
		return this;
	}

	@Override
	public ErrorResponse.Builder headers(Consumer<HttpHeaders> headersConsumer) {
		return this;
	}

	@Override
	public ErrorResponse.Builder detail(String detail) {
		this.problemDetail.setDetail(detail);
		return this;
	}

	@Override
	public ErrorResponse.Builder detailMessageCode(String messageCode) {
		Assert.notNull(messageCode, "`detailMessageCode` is required");
		this.detailMessageCode = messageCode;
		return this;
	}

	@Override
	public ErrorResponse.Builder detailMessageArguments(Object... messageArguments) {
		this.detailMessageArguments = messageArguments;
		return this;
	}

	@Override
	public ErrorResponse.Builder type(URI type) {
		this.problemDetail.setType(type);
		return this;
	}

	@Override
	public ErrorResponse.Builder title(@Nullable String title) {
		this.problemDetail.setTitle(title);
		return this;
	}

	@Override
	public ErrorResponse.Builder titleMessageCode(String messageCode) {
		Assert.notNull(messageCode, "`titleMessageCode` is required");
		this.titleMessageCode = messageCode;
		return this;
	}

	@Override
	public ErrorResponse.Builder instance(@Nullable URI instance) {
		this.problemDetail.setInstance(instance);
		return this;
	}

	@Override
	public ErrorResponse.Builder property(String name, Object value) {
		this.problemDetail.setProperty(name, value);
		return this;
	}

	@Override
	public ErrorResponse build() {
		return new SimpleErrorResponse(
				this.exception, this.statusCode, this.headers, this.problemDetail,
				this.detailMessageCode, this.detailMessageArguments, this.titleMessageCode);
	}


	/**
	 * Simple container for {@code ErrorResponse} values.
	 */
	private static class SimpleErrorResponse implements ErrorResponse {

		private final Throwable exception;

		private final HttpStatusCode statusCode;

		private final HttpHeaders headers;

		private final ProblemDetail problemDetail;

		private final String detailMessageCode;

		@Nullable
		private final Object[] detailMessageArguments;

		private final String titleMessageCode;

		SimpleErrorResponse(
				Throwable ex, HttpStatusCode statusCode, @Nullable HttpHeaders headers, ProblemDetail problemDetail,
				String detailMessageCode, @Nullable Object[] detailMessageArguments, String titleMessageCode) {

			this.exception = ex;
			this.statusCode = statusCode;
			this.headers = (headers != null ? headers : HttpHeaders.EMPTY);
			this.problemDetail = problemDetail;
			this.detailMessageCode = detailMessageCode;
			this.detailMessageArguments = detailMessageArguments;
			this.titleMessageCode = titleMessageCode;
		}

		@Override
		public HttpStatusCode getStatusCode() {
			return this.statusCode;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public ProblemDetail getBody() {
			return this.problemDetail;
		}

		@Override
		public String getDetailMessageCode() {
			return this.detailMessageCode;
		}

		@Override
		public Object[] getDetailMessageArguments() {
			return this.detailMessageArguments;
		}

		@Override
		public String getTitleMessageCode() {
			return this.titleMessageCode;
		}

		@Override
		public String toString() {
			return "ErrorResponse{status=" + this.statusCode + ", " +
					"headers=" + this.headers + ", body=" + this.problemDetail + ", " +
					"exception=" + this.exception + "}";
		}
	}

}
