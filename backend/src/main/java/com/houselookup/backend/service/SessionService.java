package com.houselookup.backend.service;

import com.houselookup.backend.model.User;
import com.houselookup.backend.model.UserSession;
import com.houselookup.backend.repository.UserRepository;
import com.houselookup.backend.repository.UserSessionRepository;
import com.houselookup.backend.util.AuthCookieProperties;
import com.houselookup.backend.util.SecurityUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

@Service
public class SessionService {
  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

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

  @PostConstruct
  void logConfiguration() {
    log.info(
        "Auth cookie configured name={} secure={} sameSite={} ttlDays={}",
        authCookieProperties.getCookieName(),
        authCookieProperties.isCookieSecure(),
        authCookieProperties.getCookieSameSite(),
        authCookieProperties.getCookieTtlDays());
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
    session = sessionRepository.save(session);
    log.info("Session created userId={} sessionId={} expiresAt={}", userId, session.getId(), expiry);

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

  @Transactional
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
      sessionRepository
          .findBySessionTokenHash(hashed)
          .ifPresent(
              session -> {
                sessionRepository.delete(session);
                log.info(
                    "Session destroyed userId={} sessionId={}",
                    session.getUser().getId(),
                    session.getId());
              });
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
    int deleted = sessionRepository.deleteExpired(Instant.now());
    if (deleted > 0) {
      log.info("Expired sessions cleaned up count={}", deleted);
    }
  }
}
