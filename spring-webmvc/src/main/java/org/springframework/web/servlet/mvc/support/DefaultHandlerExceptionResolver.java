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

package org.springframework.web.servlet.mvc.support;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

/**
 * The default implementation of the {@link org.springframework.web.servlet.HandlerExceptionResolver}
 * interface, resolving standard Spring MVC exceptions and translating them to corresponding
 * HTTP status codes.
 *
 * <p>This exception resolver is enabled by default in the common Spring
 * {@link org.springframework.web.servlet.DispatcherServlet}.
 *
 * <table>
 * <caption>Supported Exceptions</caption>
 * <thead>
 * <tr>
 * <th class="colFirst">Exception</th>
 * <th class="colLast">HTTP Status Code</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr class="altColor">
 * <td><p>HttpRequestMethodNotSupportedException</p></td>
 * <td><p>405 (SC_METHOD_NOT_ALLOWED)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>HttpMediaTypeNotSupportedException</p></td>
 * <td><p>415 (SC_UNSUPPORTED_MEDIA_TYPE)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>HttpMediaTypeNotAcceptableException</p></td>
 * <td><p>406 (SC_NOT_ACCEPTABLE)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>MissingPathVariableException</p></td>
 * <td><p>500 (SC_INTERNAL_SERVER_ERROR)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>MissingServletRequestParameterException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>MissingServletRequestPartException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>ServletRequestBindingException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>ConversionNotSupportedException</p></td>
 * <td><p>500 (SC_INTERNAL_SERVER_ERROR)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>TypeMismatchException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>HttpMessageNotReadableException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>HttpMessageNotWritableException</p></td>
 * <td><p>500 (SC_INTERNAL_SERVER_ERROR)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>MethodArgumentNotValidException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>BindException</p></td>
 * <td><p>400 (SC_BAD_REQUEST)</p></td>
 * </tr>
 * <tr class="rowColor">
 * <td><p>NoHandlerFoundException</p></td>
 * <td><p>404 (SC_NOT_FOUND)</p></td>
 * </tr>
 * <tr class="altColor">
 * <td><p>AsyncRequestTimeoutException</p></td>
 * <td><p>503 (SC_SERVICE_UNAVAILABLE)</p></td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
 */
public class DefaultHandlerExceptionResolver extends AbstractHandlerExceptionResolver {

	/**
	 * Log category to use when no mapped handler is found for a request.
	 * @see #pageNotFoundLogger
	 */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Additional logger to use when no mapped handler is found for a request.
	 * @see #PAGE_NOT_FOUND_LOG_CATEGORY
	 */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);


	/**
	 * Sets the {@linkplain #setOrder(int) order} to {@link #LOWEST_PRECEDENCE}.
	 */
	public DefaultHandlerExceptionResolver() {
		setOrder(Ordered.LOWEST_PRECEDENCE);
		setWarnLogCategory(getClass().getName());
	}


	@Override
	@Nullable
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		try {
			// ErrorResponse exceptions that expose HTTP response details
			if (ex instanceof ErrorResponse) {
				ModelAndView mav = null;
				if (ex instanceof HttpRequestMethodNotSupportedException) {
					mav = handleHttpRequestMethodNotSupported(
							(HttpRequestMethodNotSupportedException) ex, request, response, handler);
				}
				else if (ex instanceof HttpMediaTypeNotSupportedException) {
					mav = handleHttpMediaTypeNotSupported(
							(HttpMediaTypeNotSupportedException) ex, request, response, handler);
				}
				else if (ex instanceof HttpMediaTypeNotAcceptableException) {
					mav = handleHttpMediaTypeNotAcceptable(
							(HttpMediaTypeNotAcceptableException) ex, request, response, handler);
				}
				else if (ex instanceof MissingPathVariableException) {
					mav = handleMissingPathVariable(
							(MissingPathVariableException) ex, request, response, handler);
				}
				else if (ex instanceof MissingServletRequestParameterException) {
					mav = handleMissingServletRequestParameter(
							(MissingServletRequestParameterException) ex, request, response, handler);
				}
				else if (ex instanceof MissingServletRequestPartException) {
					mav = handleMissingServletRequestPartException(
							(MissingServletRequestPartException) ex, request, response, handler);
				}
				else if (ex instanceof ServletRequestBindingException) {
					mav = handleServletRequestBindingException(
							(ServletRequestBindingException) ex, request, response, handler);
				}
				else if (ex instanceof MethodArgumentNotValidException) {
					mav = handleMethodArgumentNotValidException(
							(MethodArgumentNotValidException) ex, request, response, handler);
				}
				else if (ex instanceof NoHandlerFoundException) {
					mav = handleNoHandlerFoundException(
							(NoHandlerFoundException) ex, request, response, handler);
				}
				else if (ex instanceof AsyncRequestTimeoutException) {
					mav = handleAsyncRequestTimeoutException(
							(AsyncRequestTimeoutException) ex, request, response, handler);
				}

				if (mav == null) {
					return handleErrorResponse((ErrorResponse) ex, request, response, handler);
				}
			}

			// Other, lower level exceptions

			if (ex instanceof ConversionNotSupportedException) {
				return handleConversionNotSupported(
						(ConversionNotSupportedException) ex, request, response, handler);
			}
			else if (ex instanceof TypeMismatchException) {
				return handleTypeMismatch(
						(TypeMismatchException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotReadableException) {
				return handleHttpMessageNotReadable(
						(HttpMessageNotReadableException) ex, request, response, handler);
			}
			else if (ex instanceof HttpMessageNotWritableException) {
				return handleHttpMessageNotWritable(
						(HttpMessageNotWritableException) ex, request, response, handler);
			}
			else if (ex instanceof BindException) {
				return handleBindException((BindException) ex, request, response, handler);
			}
		}
		catch (Exception handlerEx) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", handlerEx);
			}
		}

		return null;
	}

	/**
	 * Handle the case where no handler was found for the HTTP method.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the HttpRequestMethodNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen
	 * at the time of the exception (for example, if multipart resolution failed)
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case where no
	 * {@linkplain org.springframework.http.converter.HttpMessageConverter message converters}
	 * were found for PUT or POSTed content.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the HttpMediaTypeNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case where no
	 * {@linkplain org.springframework.http.converter.HttpMessageConverter message converters}
	 * were found that were acceptable for the client (expressed via the {@code Accept} header).
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the HttpMediaTypeNotAcceptableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case when a declared path variable does not match any extracted URI variable.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the MissingPathVariableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 * @since 4.2
	 */
	@Nullable
	protected ModelAndView handleMissingPathVariable(MissingPathVariableException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case when a required parameter is missing.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the MissingServletRequestParameterException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case where an {@linkplain RequestPart @RequestPart}, a {@link MultipartFile},
	 * or a {@code jakarta.servlet.http.Part} argument is required but is missing.
	 * <p>By default, an HTTP 400 error is sent back to the client.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleMissingServletRequestPartException(MissingServletRequestPartException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case when an unrecoverable binding exception occurs - e.g.
	 * required header, required cookie.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the exception to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleServletRequestBindingException(ServletRequestBindingException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case where an argument annotated with {@code @Valid} such as
	 * an {@link RequestBody} or {@link RequestPart} argument fails validation.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	@Nullable
	protected ModelAndView handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle the case where no handler was found during the dispatch.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the NoHandlerFoundException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen
	 * at the time of the exception (for example, if multipart resolution failed)
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 * @since 4.0
	 */
	@Nullable
	protected ModelAndView handleNoHandlerFoundException(NoHandlerFoundException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		pageNotFoundLogger.warn(ex.getMessage());
		return null;
	}

	/**
	 * Handle the case where an async request timed out.
	 * <p>The default implementation returns {@code null} in which case the
	 * exception is handled in {@link #handleErrorResponse}.
	 * @param ex the {@link AsyncRequestTimeoutException} to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen
	 * at the time of the exception (for example, if multipart resolution failed)
	 * @return an empty {@code ModelAndView} indicating the exception was handled, or
	 * {@code null} indicating the exception should be handled in {@link #handleErrorResponse}
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 * @since 4.2.8
	 */
	@Nullable
	protected ModelAndView handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		return null;
	}

	/**
	 * Handle an {@link ErrorResponse} exception.
	 * <p>The default implementation sets status and the headers of the response
	 * to those obtained from the {@code ErrorResponse}. If available, the
	 * {@link ProblemDetail#getDetail()}  is used as the message for
	 * {@link HttpServletResponse#sendError(int, String)}.
	 * @param errorResponse the exception to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 * @since 6.0
	 */
	protected ModelAndView handleErrorResponse(ErrorResponse errorResponse,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		if (!response.isCommitted()) {
			HttpHeaders headers = errorResponse.getHeaders();
			headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));

			int status = errorResponse.getStatusCode().value();
			String message = errorResponse.getBody().getDetail();
			if (message != null) {
				response.sendError(status, message);
			}
			else {
				response.sendError(status);
			}
		}
		else {
			logger.warn("Ignoring exception, response committed. : " + errorResponse);
		}

		return new ModelAndView();
	}

	/**
	 * Handle the case when a {@link org.springframework.web.bind.WebDataBinder} conversion cannot occur.
	 * <p>The default implementation sends an HTTP 500 error, and returns an empty {@code ModelAndView}.
	 * Alternatively, a fallback view could be chosen, or the ConversionNotSupportedException could be
	 * rethrown as-is.
	 * @param ex the ConversionNotSupportedException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	protected ModelAndView handleConversionNotSupported(ConversionNotSupportedException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		sendServerError(ex, request, response);
		return new ModelAndView();
	}

	/**
	 * Handle the case when a {@link org.springframework.web.bind.WebDataBinder} conversion error occurs.
	 * <p>The default implementation sends an HTTP 400 error, and returns an empty {@code ModelAndView}.
	 * Alternatively, a fallback view could be chosen, or the TypeMismatchException could be rethrown as-is.
	 * @param ex the TypeMismatchException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	protected ModelAndView handleTypeMismatch(TypeMismatchException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Handle the case where a {@linkplain org.springframework.http.converter.HttpMessageConverter message converter}
	 * cannot read from an HTTP request.
	 * <p>The default implementation sends an HTTP 400 error, and returns an empty {@code ModelAndView}.
	 * Alternatively, a fallback view could be chosen, or the HttpMessageNotReadableException could be
	 * rethrown as-is.
	 * @param ex the HttpMessageNotReadableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	protected ModelAndView handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Handle the case where a
	 * {@linkplain org.springframework.http.converter.HttpMessageConverter message converter}
	 * cannot write to an HTTP request.
	 * <p>The default implementation sends an HTTP 500 error, and returns an empty {@code ModelAndView}.
	 * Alternatively, a fallback view could be chosen, or the HttpMessageNotWritableException could
	 * be rethrown as-is.
	 * @param ex the HttpMessageNotWritableException to be handled
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 */
	protected ModelAndView handleHttpMessageNotWritable(HttpMessageNotWritableException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws IOException {

		sendServerError(ex, request, response);
		return new ModelAndView();
	}

	/**
	 * Handle the case where an {@linkplain ModelAttribute @ModelAttribute} method
	 * argument has binding or validation errors and is not followed by another
	 * method argument of type {@link BindingResult}.
	 * <p>By default, an HTTP 400 error is sent back to the client.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler
	 * @return an empty {@code ModelAndView} indicating the exception was handled
	 * @throws IOException potentially thrown from {@link HttpServletResponse#sendError}
	 * @deprecated as of 6.0 since {@link org.springframework.web.method.annotation.ModelAttributeMethodProcessor}
	 * now raises the {@link MethodArgumentNotValidException} subclass instead.
	 */
	@Deprecated(since = "6.0", forRemoval = true)
	protected ModelAndView handleBindException(BindException ex, HttpServletRequest request,
			HttpServletResponse response, @Nullable Object handler) throws IOException {

		response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		return new ModelAndView();
	}

	/**
	 * Invoked to send a server error. Sets the status to 500 and also sets the
	 * request attribute "jakarta.servlet.error.exception" to the Exception.
	 */
	protected void sendServerError(Exception ex, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex);
		response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

}
