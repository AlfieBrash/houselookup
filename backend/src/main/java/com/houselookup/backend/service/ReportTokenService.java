package com.houselookup.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.houselookup.backend.model.ReportDownloadToken;
import com.houselookup.backend.model.User;
import com.houselookup.backend.repository.ReportDownloadTokenRepository;
import com.houselookup.backend.util.SecurityUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportTokenService {
  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_PROCESSING = "PROCESSING";
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_FAILED = "FAILED";

  @Value("${app.report.token-ttl-minutes:15}")
  private long tokenTtlMinutes;

  private final ReportDownloadTokenRepository tokenRepository;
  private final ObjectMapper objectMapper;

  public ReportTokenService(ReportDownloadTokenRepository tokenRepository, ObjectMapper objectMapper) {
    this.tokenRepository = tokenRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public String createToken(User user, Map<String, Object> payload) {
    String token = SecurityUtil.generateSecureToken();
    String tokenHash = SecurityUtil.hash(token);
    try {
      String payloadJson = objectMapper.writeValueAsString(payload);
      Instant expiry = Instant.now().plus(tokenTtlMinutes, ChronoUnit.MINUTES);
      ReportDownloadToken tokenEntity =
          new ReportDownloadToken(user, tokenHash, payloadJson, STATUS_PENDING, expiry);
      tokenRepository.save(tokenEntity);
      return token;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not create report token.");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> parsePayload(String payloadJson) {
    try {
      return objectMapper.readValue(payloadJson, Map.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not read report token payload.");
    }
  }

  @Transactional
  public ReportDownloadToken claimToken(String token) {
    String hash = SecurityUtil.hash(token);
    ReportDownloadToken record =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new IllegalArgumentException("Download token not found."));

    if (record.getExpiresAt().isBefore(Instant.now())) {
      if (!STATUS_FAILED.equals(record.getStatus())) {
        record.setStatus("EXPIRED");
      }
      throw new IllegalArgumentException("Download token expired.");
    }

    if (!STATUS_PENDING.equals(record.getStatus())) {
      throw new IllegalStateException("Download token already used.");
    }

    record.setStatus(STATUS_PROCESSING);
    return record;
  }

  @Transactional
  public void markCompleted(ReportDownloadToken token) {
    token.setStatus(STATUS_COMPLETED);
    token.setUsedAt(Instant.now());
    tokenRepository.save(token);
  }

  @Transactional
  public void markFailed(ReportDownloadToken token, String error) {
    token.setStatus(STATUS_FAILED);
    token.setErrorMessage(error);
    tokenRepository.save(token);
  }

  public String getStatusFailed() {
    return STATUS_FAILED;
  }
}
