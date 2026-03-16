package com.houselookup.backend.service;

import com.houselookup.backend.model.User;
import com.houselookup.backend.model.UserSession;
import com.houselookup.backend.repository.UserRepository;
import com.houselookup.backend.repository.UserSessionRepository;
import com.houselookup.backend.util.AuthCookieProperties;
import com.houselookup.backend.util.SecurityUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

@Service
public class SessionService {
  private final UserSessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final AuthCookieProperties authCookieProperties;

  public SessionService(
      UserSessionRepository sessionRepository,
      UserRepository userRepository,
      AuthCookieProperties authCookieProperties) {
    this.sessionRepository = sessionRepository;
    this.userRepository = userRepository;
    this.authCookieProperties = authCookieProperties;
  }

  @Transactional
  public String createSession(
      long userId, String ipAddress, String userAgent, HttpServletResponse response) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));

    cleanupExpiredSessions();
    String rawToken = SecurityUtil.generateSecureToken();
    String tokenHash = SecurityUtil.hash(rawToken);
    Instant now = Instant.now();
    Instant expiry = now.plus(authCookieProperties.getCookieTtlDays(), ChronoUnit.DAYS);

    UserSession session = new UserSession(user, tokenHash, expiry, now);
    session.setIp(ipAddress);
    session.setUserAgent(userAgent);
    sessionRepository.save(session);

    clearSessionCookie(response);
    ResponseCookie cookie =
        ResponseCookie.from(authCookieProperties.getCookieName(), rawToken)
            .httpOnly(true)
            .secure(authCookieProperties.isCookieSecure())
            .sameSite(authCookieProperties.getCookieSameSite())
            .path("/")
            .maxAge(Duration.ofDays(authCookieProperties.getCookieTtlDays()))
            .build();
    response.addHeader("Set-Cookie", cookie.toString());
    return rawToken;
  }

  @Transactional(readOnly = true)
  public Long resolveUserId(HttpServletRequest request) {
    cleanupExpiredSessions();
    Cookie cookie = WebUtils.getCookie(request, authCookieProperties.getCookieName());
    if (cookie == null || cookie.getValue() == null || cookie.getValue().trim().isEmpty()) {
      return null;
    }
    String hashed = SecurityUtil.hash(cookie.getValue());
    return sessionRepository
        .findBySessionTokenHash(hashed)
        .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
        .map(session -> session.getUser().getId())
        .orElse(null);
  }

  @Transactional
  public void destroySession(HttpServletRequest request, HttpServletResponse response) {
    Cookie cookie = WebUtils.getCookie(request, authCookieProperties.getCookieName());
    if (cookie != null && cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
      String hashed = SecurityUtil.hash(cookie.getValue());
      sessionRepository.findBySessionTokenHash(hashed).ifPresent(sessionRepository::delete);
    }
    clearSessionCookie(response);
  }

  private void clearSessionCookie(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(authCookieProperties.getCookieName(), "")
            .path("/")
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .secure(authCookieProperties.isCookieSecure())
            .sameSite(authCookieProperties.getCookieSameSite())
            .build();
    response.addHeader("Set-Cookie", cookie.toString());
  }

  @Transactional
  public void cleanupExpiredSessions() {
    sessionRepository.deleteExpired(Instant.now());
  }
}
