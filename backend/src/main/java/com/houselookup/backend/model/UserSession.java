package com.houselookup.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "user_sessions",
    indexes = {@Index(name = "idx_user_sessions_token", columnList = "session_token_hash", unique = true)})
public class UserSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_sessions_user"))
  private User user;

  @Column(name = "session_token_hash", nullable = false, length = 255, unique = true)
  private String sessionTokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "ip", length = 255)
  private String ip;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  public UserSession() {}

  public UserSession(User user, String sessionTokenHash, Instant expiresAt, Instant createdAt) {
    this.user = user;
    this.sessionTokenHash = sessionTokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
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

  public String getSessionTokenHash() {
    return sessionTokenHash;
  }

  public void setSessionTokenHash(String sessionTokenHash) {
    this.sessionTokenHash = sessionTokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
