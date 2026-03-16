package com.houselookup.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "user_credits",
    uniqueConstraints = {@UniqueConstraint(name = "uk_user_credits_user_id", columnNames = "user_id")})
public class UserCredits {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_credits_user"))
  private User user;

  @Column(nullable = false)
  private Integer balance = 0;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UserCredits() {}

  public UserCredits(User user) {
    this.user = user;
    this.balance = 0;
    this.updatedAt = Instant.now();
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

  public Integer getBalance() {
    return balance;
  }

  public void setBalance(Integer balance) {
    this.balance = balance;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
