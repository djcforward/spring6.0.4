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

package org.springframework.web.servlet.view.xml;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamResult;

import jakarta.xml.bind.JAXBElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.oxm.Marshaller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.testfixture.servlet.MockHttpServletRequest;
import org.springframework.web.testfixture.servlet.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 */
public class MarshallingViewTests {

	private Marshaller marshallerMock;

	private MarshallingView view;


	@BeforeEach
	public void createView() throws Exception {
		marshallerMock = mock(Marshaller.class);
		view = new MarshallingView(marshallerMock);
	}


	@Test
	public void getContentType() {
		assertThat(view.getContentType()).as("Invalid content type").isEqualTo("application/xml");
	}

	@Test
	public void isExposePathVars() {
		assertThat(view.isExposePathVariables()).as("Must not expose path variables").isFalse();
	}

	@Test
	public void isExposePathVarsDefaultConstructor() {
		assertThat(new MarshallingView().isExposePathVariables()).as("Must not expose path variables").isFalse();
	}

	@Test
	public void renderModelKey() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		view.setModelKey(modelKey);
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(Object.class)).willReturn(true);
		marshallerMock.marshal(eq(toBeMarshalled), isA(StreamResult.class));

		view.render(model, request, response);
		assertThat(response.getContentType()).as("Invalid content type").isEqualTo("application/xml");
		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
	}

	@Test
	public void renderModelKeyWithJaxbElement() throws Exception {
		String toBeMarshalled = "value";
		String modelKey = "key";
		view.setModelKey(modelKey);
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, new JAXBElement<>(new QName("model"), String.class, toBeMarshalled));

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(String.class)).willReturn(true);
		marshallerMock.marshal(eq(toBeMarshalled), isA(StreamResult.class));

		view.render(model, request, response);
		assertThat(response.getContentType()).as("Invalid content type").isEqualTo("application/xml");
		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
	}

	@Test
	public void renderInvalidModelKey() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		view.setModelKey("invalidKey");
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatIllegalStateException().isThrownBy(() ->
				view.render(model, request, response));

		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
	}

	@Test
	public void renderNullModelValue() throws Exception {
		String modelKey = "key";
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, null);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatIllegalStateException().isThrownBy(() ->
				view.render(model, request, response));

		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
	}

	@Test
	public void renderModelKeyUnsupported() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		view.setModelKey(modelKey);
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(Object.class)).willReturn(false);

		assertThatIllegalStateException().isThrownBy(() ->
				view.render(model, request, response));
	}

	@Test
	public void renderNoModelKey() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(Object.class)).willReturn(true);

		view.render(model, request, response);
		assertThat(response.getContentType()).as("Invalid content type").isEqualTo("application/xml");
		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
		verify(marshallerMock).marshal(eq(toBeMarshalled), isA(StreamResult.class));
	}

	@Test
	public void renderNoModelKeyAndBindingResultFirst() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		Map<String, Object> model = new LinkedHashMap<>();
		model.put(BindingResult.MODEL_KEY_PREFIX + modelKey, new BeanPropertyBindingResult(toBeMarshalled, modelKey));
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(BeanPropertyBindingResult.class)).willReturn(true);
		given(marshallerMock.supports(Object.class)).willReturn(true);

		view.render(model, request, response);
		assertThat(response.getContentType()).as("Invalid content type").isEqualTo("application/xml");
		assertThat(response.getContentLength()).as("Invalid content length").isEqualTo(0);
		verify(marshallerMock).marshal(eq(toBeMarshalled), isA(StreamResult.class));
	}

	@Test
	public void testRenderUnsupportedModel() throws Exception {
		Object toBeMarshalled = new Object();
		String modelKey = "key";
		Map<String, Object> model = new HashMap<>();
		model.put(modelKey, toBeMarshalled);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		given(marshallerMock.supports(Object.class)).willReturn(false);

		assertThatIllegalStateException().isThrownBy(() ->
				view.render(model, request, response));
	}

}
