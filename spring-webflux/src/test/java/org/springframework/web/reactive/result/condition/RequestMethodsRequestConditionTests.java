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

package org.springframework.web.reactive.result.condition;

import java.net.URISyntaxException;
import java.util.Collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.testfixture.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.testfixture.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
import static org.springframework.web.bind.annotation.RequestMethod.OPTIONS;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

/**
 * Unit tests for {@link RequestMethodsRequestCondition}.
 *
 * @author Rossen Stoyanchev
 */
public class RequestMethodsRequestConditionTests {

	// TODO: custom method, CORS pre-flight (see @Disabled)

	@Test
	public void getMatchingCondition() throws Exception {
		testMatch(new RequestMethodsRequestCondition(GET), GET);
		testMatch(new RequestMethodsRequestCondition(GET, POST), GET);
		testNoMatch(new RequestMethodsRequestCondition(GET), POST);
	}

	@Test
	public void getMatchingConditionWithHttpHead() throws Exception {
		testMatch(new RequestMethodsRequestCondition(HEAD), HEAD);
		testMatch(new RequestMethodsRequestCondition(GET), GET);
		testNoMatch(new RequestMethodsRequestCondition(POST), HEAD);
	}

	@Test
	public void getMatchingConditionWithEmptyConditions() throws Exception {
		RequestMethodsRequestCondition condition = new RequestMethodsRequestCondition();
		for (RequestMethod method : RequestMethod.values()) {
			if (method != OPTIONS) {
				ServerWebExchange exchange = getExchange(method.name());
				assertThat(condition.getMatchingCondition(exchange)).isNotNull();
			}
		}
		testNoMatch(condition, OPTIONS);
	}

	@Test
	@Disabled
	public void getMatchingConditionWithCustomMethod() throws Exception {
		ServerWebExchange exchange = getExchange("PROPFIND");
		assertThat(new RequestMethodsRequestCondition().getMatchingCondition(exchange)).isNotNull();
		assertThat(new RequestMethodsRequestCondition(GET, POST).getMatchingCondition(exchange)).isNull();
	}

	@Test
	@Disabled
	public void getMatchingConditionWithCorsPreFlight() throws Exception {
		ServerWebExchange exchange = getExchange("OPTIONS");
		exchange.getRequest().getHeaders().add("Origin", "https://example.com");
		exchange.getRequest().getHeaders().add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT");

		assertThat(new RequestMethodsRequestCondition().getMatchingCondition(exchange)).isNotNull();
		assertThat(new RequestMethodsRequestCondition(PUT).getMatchingCondition(exchange)).isNotNull();
		assertThat(new RequestMethodsRequestCondition(DELETE).getMatchingCondition(exchange)).isNull();
	}

	@Test
	public void compareTo() throws Exception {
		RequestMethodsRequestCondition c1 = new RequestMethodsRequestCondition(GET, HEAD);
		RequestMethodsRequestCondition c2 = new RequestMethodsRequestCondition(POST);
		RequestMethodsRequestCondition c3 = new RequestMethodsRequestCondition();

		ServerWebExchange exchange = getExchange("GET");

		int result = c1.compareTo(c2, exchange);
		assertThat(result < 0).as("Invalid comparison result: " + result).isTrue();

		result = c2.compareTo(c1, exchange);
		assertThat(result > 0).as("Invalid comparison result: " + result).isTrue();

		result = c2.compareTo(c3, exchange);
		assertThat(result < 0).as("Invalid comparison result: " + result).isTrue();

		result = c1.compareTo(c1, exchange);
		assertThat(result).as("Invalid comparison result ").isEqualTo(0);
	}

	@Test
	public void combine() {
		RequestMethodsRequestCondition condition1 = new RequestMethodsRequestCondition(GET);
		RequestMethodsRequestCondition condition2 = new RequestMethodsRequestCondition(POST);

		RequestMethodsRequestCondition result = condition1.combine(condition2);
		assertThat(result.getContent()).hasSize(2);
	}


	private void testMatch(RequestMethodsRequestCondition condition, RequestMethod method) throws Exception {
		ServerWebExchange exchange = getExchange(method.name());
		RequestMethodsRequestCondition actual = condition.getMatchingCondition(exchange);
		assertThat(actual).isNotNull();
		assertThat(actual.getContent()).isEqualTo(Collections.singleton(method));
	}

	private void testNoMatch(RequestMethodsRequestCondition condition, RequestMethod method) throws Exception {
		ServerWebExchange exchange = getExchange(method.name());
		assertThat(condition.getMatchingCondition(exchange)).isNull();
	}

	private ServerWebExchange getExchange(String method) throws URISyntaxException {
		return MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), "/"));
	}

}
