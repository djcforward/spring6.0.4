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

package org.springframework.aot.nativex;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.codec.StringDecoder;
import org.springframework.util.MimeType;

/**
 * Tests for {@link ReflectionHintsWriter}.
 *
 * @author Sebastien Deleuze
 */
public class ReflectionHintsWriterTests {

	@Test
	void empty() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		assertEquals("[]", hints);
	}

	@Test
	void one() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(StringDecoder.class, builder -> builder
				.onReachableType(String.class)
				.withMembers(MemberCategory.PUBLIC_FIELDS, MemberCategory.DECLARED_FIELDS,
						MemberCategory.INTROSPECT_PUBLIC_CONSTRUCTORS, MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
						MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
						MemberCategory.INTROSPECT_PUBLIC_METHODS, MemberCategory.INTROSPECT_DECLARED_METHODS,
						MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_DECLARED_METHODS,
						MemberCategory.PUBLIC_CLASSES, MemberCategory.DECLARED_CLASSES)
				.withField("DEFAULT_CHARSET")
				.withField("defaultCharset")
				.withConstructor(TypeReference.listOf(List.class, boolean.class, MimeType.class), ExecutableMode.INTROSPECT)
				.withMethod("setDefaultCharset", List.of(TypeReference.of(Charset.class)), ExecutableMode.INVOKE)
				.withMethod("getDefaultCharset", Collections.emptyList(), ExecutableMode.INTROSPECT));
		assertEquals("""
				[
					{
						"name": "org.springframework.core.codec.StringDecoder",
						"condition": { "typeReachable": "java.lang.String" },
						"allPublicFields": true,
						"allDeclaredFields": true,
						"queryAllPublicConstructors": true,
						"queryAllDeclaredConstructors": true,
						"allPublicConstructors": true,
						"allDeclaredConstructors": true,
						"queryAllPublicMethods": true,
						"queryAllDeclaredMethods": true,
						"allPublicMethods": true,
						"allDeclaredMethods": true,
						"allPublicClasses": true,
						"allDeclaredClasses": true,
						"fields": [
							{ "name": "DEFAULT_CHARSET" },
							{ "name": "defaultCharset" }
						],
						"methods": [
							{ "name": "setDefaultCharset", "parameterTypes": [ "java.nio.charset.Charset" ] }
						],
						"queriedMethods":  [
							{ "name": "<init>", "parameterTypes": [ "java.util.List", "boolean", "org.springframework.util.MimeType" ] },
							{ "name": "getDefaultCharset", "parameterTypes": [ ] }
						]
					}
				]""", hints);
	}

	@Test
	void two() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(Integer.class, builder -> {
		});
		hints.registerType(Long.class, builder -> {
		});

		assertEquals("""
				[
					{ "name": "java.lang.Integer" },
					{ "name": "java.lang.Long" }
				]""", hints);
	}

	@Test
	void queriedMethods() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(Integer.class, builder -> builder.withMethod("parseInt",
				TypeReference.listOf(String.class), ExecutableMode.INTROSPECT));

		assertEquals("""
				[
					{
						"name": "java.lang.Integer",
						"queriedMethods": [
							{
								"name": "parseInt",
								"parameterTypes": ["java.lang.String"]
							}
						]
					}
				]
				""", hints);
	}

	@Test
	void methods() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(Integer.class, builder -> builder.withMethod("parseInt",
				TypeReference.listOf(String.class), ExecutableMode.INVOKE));

		assertEquals("""
				[
					{
						"name": "java.lang.Integer",
						"methods": [
							{
								"name": "parseInt",
								"parameterTypes": ["java.lang.String"]
							}
						]
					}
				]
				""", hints);
	}

	@Test
	void methodWithInnerClassParameter() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(Integer.class, builder -> builder.withMethod("test",
				TypeReference.listOf(Inner.class), ExecutableMode.INVOKE));

		assertEquals("""
				[
					{
						"name": "java.lang.Integer",
						"methods": [
							{
								"name": "test",
								"parameterTypes": ["org.springframework.aot.nativex.ReflectionHintsWriterTests$Inner"]
							}
						]
					}
				]
				""", hints);
	}

	@Test
	void methodAndQueriedMethods() throws JSONException {
		ReflectionHints hints = new ReflectionHints();
		hints.registerType(Integer.class, builder -> builder.withMethod("parseInt",
				TypeReference.listOf(String.class), ExecutableMode.INVOKE));
		hints.registerType(Integer.class, builder -> builder.withMethod("parseInt",
				TypeReference.listOf(String.class, int.class), ExecutableMode.INTROSPECT));

		assertEquals("""
				[
					{
						"name": "java.lang.Integer",
						"queriedMethods": [
							{
								"name": "parseInt",
								"parameterTypes": ["java.lang.String", "int"]
							}
						],
						"methods": [
							{
								"name": "parseInt",
								"parameterTypes": ["java.lang.String"]
							}
						]
					}
				]
				""", hints);
	}

	private void assertEquals(String expectedString, ReflectionHints hints) throws JSONException {
		StringWriter out = new StringWriter();
		BasicJsonWriter writer = new BasicJsonWriter(out, "\t");
		ReflectionHintsWriter.INSTANCE.write(writer, hints);
		JSONAssert.assertEquals(expectedString, out.toString(), JSONCompareMode.NON_EXTENSIBLE);
	}


	static class Inner {

	}

}
