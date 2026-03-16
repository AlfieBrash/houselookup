package com.houselookup.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "payment_transactions",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_payment_transactions_provider_session_id", columnNames = "provider_session_id")
    })
public class PaymentTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_transactions_user"))
  private User user;

  @Column(name = "provider_session_id", nullable = false, length = 255, unique = true)
  private String providerSessionId;

  @Column(name = "provider", nullable = false, length = 64)
  private String provider;

  @Column(name = "package_size", nullable = false)
  private Integer packageSize;

  @Column(name = "amount_cents", nullable = false)
  private Long amountCents;

  @Column(nullable = false, length = 16)
  private String currency;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PaymentTransaction() {}

  public PaymentTransaction(
      User user,
      String providerSessionId,
      String provider,
      Integer packageSize,
      Long amountCents,
      String currency,
      String status,
      String idempotencyKey) {
    this.user = user;
    this.providerSessionId = providerSessionId;
    this.provider = provider;
    this.packageSize = packageSize;
    this.amountCents = amountCents;
    this.currency = currency;
    this.status = status;
    this.idempotencyKey = idempotencyKey;
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
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

  public String getProviderSessionId() {
    return providerSessionId;
  }

  public void setProviderSessionId(String providerSessionId) {
    this.providerSessionId = providerSessionId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public Integer getPackageSize() {
    return packageSize;
  }

  public void setPackageSize(Integer packageSize) {
    this.packageSize = packageSize;
  }

  public Long getAmountCents() {
    return amountCents;
  }

  public void setAmountCents(Long amountCents) {
    this.amountCents = amountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
