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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportTokenService {
  private static final Logger log = LoggerFactory.getLogger(ReportTokenService.class);

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
      tokenEntity = tokenRepository.save(tokenEntity);
      log.info(
          "Report download token created tokenId={} userId={} expiresAt={}",
          tokenEntity.getId(),
          user.getId(),
          expiry);
      return token;
    } catch (JsonProcessingException e) {
      log.error("Report download token creation failed userId={}", user.getId(), e);
      throw new IllegalStateException("Could not create report token.");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> parsePayload(String payloadJson) {
    try {
      return objectMapper.readValue(payloadJson, Map.class);
    } catch (JsonProcessingException e) {
      log.error("Report download token payload parsing failed", e);
      throw new IllegalStateException("Could not read report token payload.");
    }
  }

  @Transactional
  public ReportDownloadToken claimToken(String token) {
    String hash = SecurityUtil.hash(token);
    ReportDownloadToken record =
        tokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () -> {
                  log.warn("Report download token claim rejected reason=not_found");
                  return new IllegalArgumentException("Download token not found.");
                });

    if (record.getExpiresAt().isBefore(Instant.now())) {
      if (!STATUS_FAILED.equals(record.getStatus())) {
        record.setStatus("EXPIRED");
      }
      log.warn(
          "Report download token claim rejected reason=expired tokenId={} userId={}",
          record.getId(),
          record.getUser().getId());
      throw new IllegalArgumentException("Download token expired.");
    }

    if (!STATUS_PENDING.equals(record.getStatus())) {
      log.warn(
          "Report download token claim rejected reason=already_used tokenId={} userId={} status={}",
          record.getId(),
          record.getUser().getId(),
          record.getStatus());
      throw new IllegalStateException("Download token already used.");
    }

    record.setStatus(STATUS_PROCESSING);
    log.info(
        "Report download token claimed tokenId={} userId={}",
        record.getId(),
        record.getUser().getId());
    return record;
  }

  @Transactional
  public void markCompleted(ReportDownloadToken token) {
    token.setStatus(STATUS_COMPLETED);
    token.setUsedAt(Instant.now());
    tokenRepository.save(token);
    log.info(
        "Report download token completed tokenId={} userId={}",
        token.getId(),
        token.getUser().getId());
  }

  @Transactional
  public void markFailed(ReportDownloadToken token, String error) {
    token.setStatus(STATUS_FAILED);
    token.setErrorMessage(error);
    tokenRepository.save(token);
    log.warn(
        "Report download token failed tokenId={} userId={} error={}",
        token.getId(),
        token.getUser().getId(),
        error);
  }

  public String getStatusFailed() {
    return STATUS_FAILED;
  }
}
