package com.houselookup.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "report_download_tokens",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_report_download_tokens_token_hash",
          columnNames = "token_hash")
    })
public class ReportDownloadToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_report_download_tokens_user"))
  private User user;

  @Column(name = "token_hash", nullable = false, length = 255, unique = true)
  private String tokenHash;

  @Column(name = "request_payload_json", nullable = false, columnDefinition = "TEXT")
  private String requestPayloadJson;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  public ReportDownloadToken() {}

  public ReportDownloadToken(
      User user,
      String tokenHash,
      String requestPayloadJson,
      String status,
      Instant expiresAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.requestPayloadJson = requestPayloadJson;
    this.status = status;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getRequestPayloadJson() {
    return requestPayloadJson;
  }

  public void setRequestPayloadJson(String requestPayloadJson) {
    this.requestPayloadJson = requestPayloadJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(Instant usedAt) {
    this.usedAt = usedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
