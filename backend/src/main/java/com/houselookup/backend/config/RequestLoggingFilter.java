package com.houselookup.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
  public static final String REQUEST_ID_MDC_KEY = "requestId";
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    long startedAt = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      logRequest(request, response, durationMs);
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }

  private void logRequest(
      HttpServletRequest request, HttpServletResponse response, long durationMs) {
    int status = response.getStatus();
    String method = request.getMethod();
    String uri = request.getRequestURI();
    String clientIp = clientIp(request);

    if ("OPTIONS".equalsIgnoreCase(method) && status < 400) {
      log.debug(
          "HTTP request completed method={} uri={} status={} durationMs={} clientIp={}",
          method,
          uri,
          status,
          durationMs,
          clientIp);
      return;
    }

    if (status >= 500) {
      log.warn(
          "HTTP request completed with server error method={} uri={} status={} durationMs={} clientIp={}",
          method,
          uri,
          status,
          durationMs,
          clientIp);
      return;
    }

    log.info(
        "HTTP request completed method={} uri={} status={} durationMs={} clientIp={}",
        method,
        uri,
        status,
        durationMs,
        clientIp);
  }

  private String resolveRequestId(HttpServletRequest request) {
    String incoming = request.getHeader(REQUEST_ID_HEADER);
    if (incoming != null && !incoming.isBlank() && incoming.length() <= 64) {
      return incoming.trim();
    }
    return UUID.randomUUID().toString();
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
