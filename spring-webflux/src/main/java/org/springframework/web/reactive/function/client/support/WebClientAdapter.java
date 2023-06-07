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

package org.springframework.web.reactive.function.client.support;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.invoker.HttpClientAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;


/**
 * {@link HttpClientAdapter} that enables an {@link HttpServiceProxyFactory} to
 * use {@link WebClient} for request execution.
 *
 * <p>Use static factory methods in this class to create an
 * {@code HttpServiceProxyFactory} configured with a given {@code WebClient}.
 *
 * @author Rossen Stoyanchev
 * @since 6.0
 */
public final class WebClientAdapter implements HttpClientAdapter {

	private final WebClient webClient;


	/**
	 * Package private constructor. See static factory methods.
	 */
	private WebClientAdapter(WebClient webClient) {
		this.webClient = webClient;
	}


	@Override
	public Mono<Void> requestToVoid(HttpRequestValues requestValues) {
		return newRequest(requestValues).retrieve().toBodilessEntity().then();
	}

	@Override
	public Mono<HttpHeaders> requestToHeaders(HttpRequestValues requestValues) {
		return newRequest(requestValues).retrieve().toBodilessEntity().map(ResponseEntity::getHeaders);
	}

	@Override
	public <T> Mono<T> requestToBody(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
		return newRequest(requestValues).retrieve().bodyToMono(bodyType);
	}

	@Override
	public <T> Flux<T> requestToBodyFlux(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
		return newRequest(requestValues).retrieve().bodyToFlux(bodyType);
	}

	@Override
	public Mono<ResponseEntity<Void>> requestToBodilessEntity(HttpRequestValues requestValues) {
		return newRequest(requestValues).retrieve().toBodilessEntity();
	}

	@Override
	public <T> Mono<ResponseEntity<T>> requestToEntity(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
		return newRequest(requestValues).retrieve().toEntity(bodyType);
	}

	@Override
	public <T> Mono<ResponseEntity<Flux<T>>> requestToEntityFlux(HttpRequestValues requestValues, ParameterizedTypeReference<T> bodyType) {
		return newRequest(requestValues).retrieve().toEntityFlux(bodyType);
	}

	@SuppressWarnings("ReactiveStreamsUnusedPublisher")
	private WebClient.RequestBodySpec newRequest(HttpRequestValues requestValues) {

		HttpMethod httpMethod = requestValues.getHttpMethod();
		Assert.notNull(httpMethod, "HttpMethod is required");

		WebClient.RequestBodyUriSpec uriSpec = this.webClient.method(httpMethod);

		WebClient.RequestBodySpec bodySpec;
		if (requestValues.getUri() != null) {
			bodySpec = uriSpec.uri(requestValues.getUri());
		}
		else if (requestValues.getUriTemplate() != null) {
			bodySpec = uriSpec.uri(requestValues.getUriTemplate(), requestValues.getUriVariables());
		}
		else {
			throw new IllegalStateException("Neither full URL nor URI template");
		}

		bodySpec.headers(headers -> headers.putAll(requestValues.getHeaders()));
		bodySpec.cookies(cookies -> cookies.putAll(requestValues.getCookies()));
		bodySpec.attributes(attributes -> attributes.putAll(requestValues.getAttributes()));

		if (requestValues.getBodyValue() != null) {
			bodySpec.bodyValue(requestValues.getBodyValue());
		}
		else if (requestValues.getBody() != null) {
			Assert.notNull(requestValues.getBodyElementType(), "Publisher body element type is required");
			bodySpec.body(requestValues.getBody(), requestValues.getBodyElementType());
		}

		return bodySpec;
	}


	/**
	 * Create a {@link WebClientAdapter} for the given {@code WebClient} instance.
	 * @param webClient the client to use
	 * @return the created adapter instance
	 */
	public static WebClientAdapter forClient(WebClient webClient) {
		return new WebClientAdapter(webClient);
	}

}
