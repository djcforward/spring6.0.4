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

package org.springframework.test.web.servlet.samples.standalone;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.Person;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Tests with asynchronous request handling.
 *
 * @author Rossen Stoyanchev
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @author Jacek Suchenia
 */
class AsyncTests {

	private final AsyncController asyncController = new AsyncController();

	private final MockMvc mockMvc = standaloneSetup(this.asyncController).build();


	@Test
	void callable() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("callable", "true"))
				.andExpect(request().asyncStarted())
				.andExpect(request().asyncResult(equalTo(new Person("Joe"))))
				.andExpect(request().asyncResult(new Person("Joe")))
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));
	}

	@Test
	void streaming() throws Exception {
		this.mockMvc.perform(get("/1").param("streaming", "true"))
				.andExpect(request().asyncStarted())
				.andDo(MvcResult::getAsyncResult) // fetch async result similar to "asyncDispatch" builder
				.andExpect(status().isOk())
				.andExpect(content().string("name=Joe"));
	}

	@Test
	void streamingSlow() throws Exception {
		this.mockMvc.perform(get("/1").param("streamingSlow", "true"))
				.andExpect(request().asyncStarted())
				.andDo(MvcResult::getAsyncResult)
				.andExpect(status().isOk())
				.andExpect(content().string("name=Joe&someBoolean=true"));
	}

	@Test
	void streamingJson() throws Exception {
		this.mockMvc.perform(get("/1").param("streamingJson", "true"))
				.andExpect(request().asyncStarted())
				.andDo(MvcResult::getAsyncResult)
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.5}"));
	}

	@Test
	void deferredResult() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("deferredResult", "true"))
				.andExpect(request().asyncStarted())
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));
	}

	@Test
	void deferredResultWithImmediateValue() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("deferredResultWithImmediateValue", "true"))
				.andExpect(request().asyncStarted())
				.andExpect(request().asyncResult(new Person("Joe")))
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));
	}

	@Test  // SPR-13079
	void deferredResultWithDelayedError() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("deferredResultWithDelayedError", "true"))
				.andExpect(request().asyncStarted())
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().is5xxServerError())
				.andExpect(content().string("Delayed Error"));
	}

	@Test
	void listenableFuture() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("listenableFuture", "true"))
				.andExpect(request().asyncStarted())
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));
	}

	@Test  // SPR-12597
	void completableFutureWithImmediateValue() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("completableFutureWithImmediateValue", "true"))
				.andExpect(request().asyncStarted())
				.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));
	}

	@Test  // SPR-12735
	void printAsyncResult() throws Exception {
		StringWriter writer = new StringWriter();

		MvcResult mvcResult = this.mockMvc.perform(get("/1").param("deferredResult", "true"))
				.andDo(print(writer))
				.andExpect(request().asyncStarted())
				.andReturn();

		assertThat(writer.toString().contains("Async started = true")).isTrue();
		writer = new StringWriter();

		this.mockMvc.perform(asyncDispatch(mvcResult))
				.andDo(print(writer))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"name\":\"Joe\",\"someDouble\":0.0,\"someBoolean\":false}"));

		assertThat(writer.toString().contains("Async started = false")).isTrue();
	}


	@RestController
	@RequestMapping(path = "/{id}", produces = "application/json")
	private static class AsyncController {

		@RequestMapping(params = "callable")
		Callable<Person> getCallable() {
			return () -> new Person("Joe");
		}

		@RequestMapping(params = "streaming")
		StreamingResponseBody getStreaming() {
			return os -> os.write("name=Joe".getBytes(StandardCharsets.UTF_8));
		}

		@RequestMapping(params = "streamingSlow")
		StreamingResponseBody getStreamingSlow() {
			return os -> {
				os.write("name=Joe".getBytes());
				try {
					Thread.sleep(200);
					os.write("&someBoolean=true".getBytes(StandardCharsets.UTF_8));
				}
				catch (InterruptedException e) {
					/* no-op */
				}
			};
		}

		@RequestMapping(params = "streamingJson")
		ResponseEntity<StreamingResponseBody> getStreamingJson() {
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
					.body(os -> os.write("{\"name\":\"Joe\",\"someDouble\":0.5}".getBytes(StandardCharsets.UTF_8)));
		}

		@RequestMapping(params = "deferredResult")
		DeferredResult<Person> getDeferredResult() {
			DeferredResult<Person> result = new DeferredResult<>();
			delay(100, () -> result.setResult(new Person("Joe")));
			return result;
		}

		@RequestMapping(params = "deferredResultWithImmediateValue")
		DeferredResult<Person> getDeferredResultWithImmediateValue() {
			DeferredResult<Person> deferredResult = new DeferredResult<>();
			deferredResult.setResult(new Person("Joe"));
			return deferredResult;
		}

		@RequestMapping(params = "deferredResultWithDelayedError")
		DeferredResult<Person> getDeferredResultWithDelayedError() {
			DeferredResult<Person> result = new DeferredResult<>();
			delay(100, () -> result.setErrorResult(new RuntimeException("Delayed Error")));
			return result;
		}

		@RequestMapping(params = "listenableFuture")
		@SuppressWarnings("deprecation")
		org.springframework.util.concurrent.ListenableFuture<Person> getListenableFuture() {
			org.springframework.util.concurrent.ListenableFutureTask<Person> futureTask =
					new org.springframework.util.concurrent.ListenableFutureTask<>(() -> new Person("Joe"));
			delay(100, futureTask);
			return futureTask;
		}

		@RequestMapping(params = "completableFutureWithImmediateValue")
		CompletableFuture<Person> getCompletableFutureWithImmediateValue() {
			CompletableFuture<Person> future = new CompletableFuture<>();
			future.complete(new Person("Joe"));
			return future;
		}

		@ExceptionHandler(Exception.class)
		@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
		String errorHandler(Exception ex) {
			return ex.getMessage();
		}

		private void delay(long millis, Runnable task) {
			Mono.delay(Duration.ofMillis(millis)).doOnTerminate(task).subscribe();
		}
	}

}
