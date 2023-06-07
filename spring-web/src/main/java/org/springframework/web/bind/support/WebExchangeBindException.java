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

package org.springframework.web.bind.support;

import java.beans.PropertyEditor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ServerWebInputException;

/**
 * {@link ServerWebInputException} subclass that indicates a data binding or
 * validation failure.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
@SuppressWarnings("serial")
public class WebExchangeBindException extends ServerWebInputException implements BindingResult {

	private final BindingResult bindingResult;


	public WebExchangeBindException(MethodParameter parameter, BindingResult bindingResult) {
		super("Validation failure", parameter, null, null, initMessageDetailArguments(bindingResult));
		this.bindingResult = bindingResult;
		getBody().setDetail("Invalid request content.");
	}

	private static Object[] initMessageDetailArguments(BindingResult bindingResult) {
		return new Object[] {
				MethodArgumentNotValidException.errorsToStringList(bindingResult.getGlobalErrors()),
				MethodArgumentNotValidException.errorsToStringList(bindingResult.getFieldErrors())
		};
	}


	/**
	 * Return the BindingResult that this BindException wraps.
	 * <p>Will typically be a BeanPropertyBindingResult.
	 * @see BeanPropertyBindingResult
	 */
	public final BindingResult getBindingResult() {
		return this.bindingResult;
	}

	@Override
	public String getObjectName() {
		return this.bindingResult.getObjectName();
	}

	@Override
	public void setNestedPath(String nestedPath) {
		this.bindingResult.setNestedPath(nestedPath);
	}

	@Override
	public String getNestedPath() {
		return this.bindingResult.getNestedPath();
	}

	@Override
	public void pushNestedPath(String subPath) {
		this.bindingResult.pushNestedPath(subPath);
	}

	@Override
	public void popNestedPath() throws IllegalStateException {
		this.bindingResult.popNestedPath();
	}

	@Override
	public void reject(String errorCode) {
		this.bindingResult.reject(errorCode);
	}

	@Override
	public void reject(String errorCode, String defaultMessage) {
		this.bindingResult.reject(errorCode, defaultMessage);
	}

	@Override
	public void reject(String errorCode, @Nullable Object[] errorArgs, @Nullable String defaultMessage) {
		this.bindingResult.reject(errorCode, errorArgs, defaultMessage);
	}

	@Override
	public void rejectValue(@Nullable String field, String errorCode) {
		this.bindingResult.rejectValue(field, errorCode);
	}

	@Override
	public void rejectValue(@Nullable String field, String errorCode, String defaultMessage) {
		this.bindingResult.rejectValue(field, errorCode, defaultMessage);
	}

	@Override
	public void rejectValue(
			@Nullable String field, String errorCode, @Nullable Object[] errorArgs, @Nullable String defaultMessage) {

		this.bindingResult.rejectValue(field, errorCode, errorArgs, defaultMessage);
	}

	@Override
	public void addAllErrors(Errors errors) {
		this.bindingResult.addAllErrors(errors);
	}

	@Override
	public boolean hasErrors() {
		return this.bindingResult.hasErrors();
	}

	@Override
	public int getErrorCount() {
		return this.bindingResult.getErrorCount();
	}

	@Override
	public List<ObjectError> getAllErrors() {
		return this.bindingResult.getAllErrors();
	}

	@Override
	public boolean hasGlobalErrors() {
		return this.bindingResult.hasGlobalErrors();
	}

	@Override
	public int getGlobalErrorCount() {
		return this.bindingResult.getGlobalErrorCount();
	}

	@Override
	public List<ObjectError> getGlobalErrors() {
		return this.bindingResult.getGlobalErrors();
	}

	@Override
	@Nullable
	public ObjectError getGlobalError() {
		return this.bindingResult.getGlobalError();
	}

	@Override
	public boolean hasFieldErrors() {
		return this.bindingResult.hasFieldErrors();
	}

	@Override
	public int getFieldErrorCount() {
		return this.bindingResult.getFieldErrorCount();
	}

	@Override
	public List<FieldError> getFieldErrors() {
		return this.bindingResult.getFieldErrors();
	}

	@Override
	@Nullable
	public FieldError getFieldError() {
		return this.bindingResult.getFieldError();
	}

	@Override
	public boolean hasFieldErrors(String field) {
		return this.bindingResult.hasFieldErrors(field);
	}

	@Override
	public int getFieldErrorCount(String field) {
		return this.bindingResult.getFieldErrorCount(field);
	}

	@Override
	public List<FieldError> getFieldErrors(String field) {
		return this.bindingResult.getFieldErrors(field);
	}

	@Override
	@Nullable
	public FieldError getFieldError(String field) {
		return this.bindingResult.getFieldError(field);
	}

	@Override
	@Nullable
	public Object getFieldValue(String field) {
		return this.bindingResult.getFieldValue(field);
	}

	@Override
	@Nullable
	public Class<?> getFieldType(String field) {
		return this.bindingResult.getFieldType(field);
	}

	@Override
	@Nullable
	public Object getTarget() {
		return this.bindingResult.getTarget();
	}

	@Override
	public Map<String, Object> getModel() {
		return this.bindingResult.getModel();
	}

	@Override
	@Nullable
	public Object getRawFieldValue(String field) {
		return this.bindingResult.getRawFieldValue(field);
	}

	@Override
	@SuppressWarnings("rawtypes")
	@Nullable
	public PropertyEditor findEditor(@Nullable String field, @Nullable Class valueType) {
		return this.bindingResult.findEditor(field, valueType);
	}

	@Override
	@Nullable
	public PropertyEditorRegistry getPropertyEditorRegistry() {
		return this.bindingResult.getPropertyEditorRegistry();
	}

	@Override
	public String[] resolveMessageCodes(String errorCode) {
		return this.bindingResult.resolveMessageCodes(errorCode);
	}

	@Override
	public String[] resolveMessageCodes(String errorCode, String field) {
		return this.bindingResult.resolveMessageCodes(errorCode, field);
	}

	@Override
	public void addError(ObjectError error) {
		this.bindingResult.addError(error);
	}

	@Override
	public void recordFieldValue(String field, Class<?> type, @Nullable Object value) {
		this.bindingResult.recordFieldValue(field, type, value);
	}

	@Override
	public void recordSuppressedField(String field) {
		this.bindingResult.recordSuppressedField(field);
	}

	@Override
	public String[] getSuppressedFields() {
		return this.bindingResult.getSuppressedFields();
	}

	/**
	 * Returns diagnostic information about the errors held in this object.
	 */
	@Override
	public String getMessage() {
		MethodParameter parameter = getMethodParameter();
		Assert.state(parameter != null, "No MethodParameter");
		StringBuilder sb = new StringBuilder("Validation failed for argument at index ")
				.append(parameter.getParameterIndex()).append(" in method: ")
				.append(parameter.getExecutable().toGenericString())
				.append(", with ").append(getErrorCount()).append(" error(s): ");
		for (ObjectError error : getAllErrors()) {
			sb.append('[').append(error).append("] ");
		}
		return sb.toString();
	}

	@Override
	public Object[] getDetailMessageArguments(MessageSource source, Locale locale) {
		return new Object[] {
				MethodArgumentNotValidException.errorsToStringList(getGlobalErrors(), source, locale),
				MethodArgumentNotValidException.errorsToStringList(getFieldErrors(), source, locale)
		};
	}

	/**
	 * Resolve global and field errors to messages with the given
	 * {@link MessageSource} and {@link Locale}.
	 * @return a Map with errors as key and resolves messages as value
	 * @since 6.0.3
	 */
	public Map<ObjectError, String> resolveErrorMessages(MessageSource messageSource, Locale locale) {
		Map<ObjectError, String> map = new LinkedHashMap<>();
		addMessages(map, getGlobalErrors(), messageSource, locale);
		addMessages(map, getFieldErrors(), messageSource, locale);
		return map;
	}

	private static void addMessages(
			Map<ObjectError, String> map, List<? extends ObjectError> errors,
			MessageSource messageSource, Locale locale) {

		List<String> messages = MethodArgumentNotValidException.errorsToStringList(errors, messageSource, locale);
		for (int i = 0; i < errors.size(); i++) {
			map.put(errors.get(i), messages.get(i));
		}
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || this.bindingResult.equals(other));
	}

	@Override
	public int hashCode() {
		return this.bindingResult.hashCode();
	}

}
