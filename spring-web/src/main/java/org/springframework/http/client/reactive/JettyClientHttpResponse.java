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

package org.springframework.http.client.reactive;

import java.net.HttpCookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.reactive.client.ReactiveResponse;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * {@link ClientHttpResponse} implementation for the Jetty ReactiveStreams HTTP client.
 *
 * @author Sebastien Deleuze
 * @since 5.1
 * @see <a href="https://github.com/jetty-project/jetty-reactive-httpclient">
 *     Jetty ReactiveStreams HttpClient</a>
 */
class JettyClientHttpResponse implements ClientHttpResponse {

	private static final Pattern SAMESITE_PATTERN = Pattern.compile("(?i).*SameSite=(Strict|Lax|None).*");


	private final ReactiveResponse reactiveResponse;

	private final Flux<DataBuffer> content;

	private final HttpHeaders headers;


	public JettyClientHttpResponse(ReactiveResponse reactiveResponse, Publisher<DataBuffer> content) {
		this.reactiveResponse = reactiveResponse;
		this.content = Flux.from(content);

		MultiValueMap<String, String> headers = new JettyHeadersAdapter(reactiveResponse.getHeaders());
		this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
	}


	@Override
	public HttpStatusCode getStatusCode() {
		return HttpStatusCode.valueOf(this.reactiveResponse.getStatus());
	}

	@Override
	public MultiValueMap<String, ResponseCookie> getCookies() {
		MultiValueMap<String, ResponseCookie> result = new LinkedMultiValueMap<>();
		List<String> cookieHeader = getHeaders().get(HttpHeaders.SET_COOKIE);
		if (cookieHeader != null) {
			cookieHeader.forEach(header ->
					HttpCookie.parse(header).forEach(cookie -> result.add(cookie.getName(),
							ResponseCookie.fromClientResponse(cookie.getName(), cookie.getValue())
									.domain(cookie.getDomain())
									.path(cookie.getPath())
									.maxAge(cookie.getMaxAge())
									.secure(cookie.getSecure())
									.httpOnly(cookie.isHttpOnly())
									.sameSite(parseSameSite(header))
									.build()))
			);
		}
		return CollectionUtils.unmodifiableMultiValueMap(result);
	}

	@Nullable
	private static String parseSameSite(String headerValue) {
		Matcher matcher = SAMESITE_PATTERN.matcher(headerValue);
		return (matcher.matches() ? matcher.group(1) : null);
	}


	@Override
	public Flux<DataBuffer> getBody() {
		return this.content;
	}

	@Override
	public HttpHeaders getHeaders() {
		return this.headers;
	}

}
