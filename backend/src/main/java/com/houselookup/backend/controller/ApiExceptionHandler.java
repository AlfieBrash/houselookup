package com.houselookup.backend.controller;

import com.houselookup.backend.config.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
      ResponseStatusException exception, HttpServletRequest request) {
    HttpStatusCode status = exception.getStatusCode();
    String message =
        exception.getReason() == null || exception.getReason().isBlank()
            ? defaultMessage(status)
            : exception.getReason();

    if (status.is5xxServerError()) {
      log.error(
          "API request failed method={} uri={} status={} message={}",
          request.getMethod(),
          request.getRequestURI(),
          status.value(),
          message,
          exception);
    } else {
      log.warn(
          "API request rejected method={} uri={} status={} message={}",
          request.getMethod(),
          request.getRequestURI(),
          status.value(),
          message);
    }

    return ResponseEntity.status(status)
        .body(buildResponse(status, request.getRequestURI(), message));
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class,
    ServletRequestBindingException.class
  })
  public ResponseEntity<ApiErrorResponse> handleBadRequest(
      Exception exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String message = "Request is invalid.";
    log.warn(
        "API request rejected method={} uri={} status={} message={} exceptionType={}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        message,
        exception.getClass().getSimpleName());
    return ResponseEntity.status(status).body(buildResponse(status, request.getRequestURI(), message));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
    String message = "HTTP method is not supported.";
    log.warn(
        "API request rejected method={} uri={} status={} message={}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        message);
    return ResponseEntity.status(status).body(buildResponse(status, request.getRequestURI(), message));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
    String message = "Content type is not supported.";
    log.warn(
        "API request rejected method={} uri={} status={} message={}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        message);
    return ResponseEntity.status(status).body(buildResponse(status, request.getRequestURI(), message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String message = "Unexpected server error.";
    log.error(
        "Unhandled API exception method={} uri={} status={}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        exception);
    return ResponseEntity.status(status).body(buildResponse(status, request.getRequestURI(), message));
  }

  private ApiErrorResponse buildResponse(HttpStatusCode status, String path, String message) {
    return new ApiErrorResponse(
        Instant.now().toString(),
        status.value(),
        errorName(status),
        message,
        path,
        MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY));
  }

  private String errorName(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved == null ? "Error" : resolved.getReasonPhrase();
  }

  private String defaultMessage(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    return resolved == null ? "Request failed." : resolved.getReasonPhrase();
  }

  public record ApiErrorResponse(
      String timestamp, int status, String error, String message, String path, String requestId) {}
}
