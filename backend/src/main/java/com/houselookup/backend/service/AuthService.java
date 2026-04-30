package com.houselookup.backend.service;

import com.houselookup.backend.model.User;
import com.houselookup.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final SessionService sessionService;
  private final CreditService creditService;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      UserRepository userRepository,
      SessionService sessionService,
      CreditService creditService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.sessionService = sessionService;
    this.creditService = creditService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public User register(String email, String password, HttpServletRequest request, HttpServletResponse response) {
    String normalizedEmail = normalizeEmail(email);
    validateEmail(normalizedEmail);
    validatePassword(password);

    if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      log.warn("Registration rejected because email already exists");
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
    }

    User user = new User(normalizedEmail, passwordEncoder.encode(password));
    user = userRepository.save(user);
    creditService.ensureRow(user.getId());

    String ip = extractClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    sessionService.createSession(user.getId(), ip, userAgent, response);
    log.info("User registered userId={} clientIp={}", user.getId(), ip);
    return user;
  }

  @Transactional
  public User login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
    String normalizedEmail = normalizeEmail(email);
    validateEmail(normalizedEmail);
    if (password == null || password.isBlank()) {
      log.warn("Login rejected because password was missing");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
    }

    User user =
        userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElse(null);

    if (user == null) {
      log.warn("Login rejected because credentials were invalid");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      log.warn("Login rejected because credentials were invalid");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    String ip = extractClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    sessionService.createSession(user.getId(), ip, userAgent, response);
    log.info("User logged in userId={} clientIp={}", user.getId(), ip);
    return user;
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    sessionService.destroySession(request, response);
  }

  public Long getCurrentUserId(HttpServletRequest request) {
    return sessionService.resolveUserId(request);
  }

  public int getCurrentCredits(Long userId) {
    return creditService.getBalance(userId);
  }

  public User requireUser(HttpServletRequest request) {
    Long userId = getCurrentUserId(request);
    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session user missing."));
  }

  private void validateEmail(String email) {
    if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is invalid.");
    }
  }

  private void validatePassword(String password) {
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
    }
    if (password.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters.");
    }
    if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must include upper, lower, and number.");
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
