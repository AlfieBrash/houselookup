package com.houselookup.backend.service;

import com.houselookup.backend.model.User;
import com.houselookup.backend.model.UserCredits;
import com.houselookup.backend.repository.UserCreditsRepository;
import com.houselookup.backend.repository.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {
  private static final Logger log = LoggerFactory.getLogger(CreditService.class);

  private final UserCreditsRepository userCreditsRepository;
  private final UserRepository userRepository;

  public CreditService(UserCreditsRepository userCreditsRepository, UserRepository userRepository) {
    this.userCreditsRepository = userCreditsRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public void ensureRow(long userId) {
    if (userCreditsRepository.findByUserId(userId) != null) {
      return;
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    UserCredits credits = new UserCredits(user);
    credits.setUpdatedAt(Instant.now());
    userCreditsRepository.save(credits);
    log.info("Credit row created userId={}", userId);
  }

  @Transactional(readOnly = true)
  public int getBalance(long userId) {
    ensureRow(userId);
    UserCredits credits = userCreditsRepository.findByUserId(userId);
    return credits == null ? 0 : credits.getBalance();
  }

  @Transactional
  public void addCredits(long userId, int amount) {
    if (amount <= 0) {
      return;
    }

    ensureRow(userId);
    UserCredits credits =
        userCreditsRepository.findForUpdateByUserId(userId);
    if (credits == null) {
      throw new IllegalStateException("Could not add credits.");
    }
    int previousBalance = credits.getBalance();
    int newBalance = previousBalance + amount;
    credits.setBalance(Integer.valueOf(newBalance));
    credits.setUpdatedAt(Instant.now());
    log.info(
        "Credits added userId={} amount={} previousBalance={} newBalance={}",
        userId,
        amount,
        previousBalance,
        newBalance);
  }

  @Transactional
  public boolean consumeOne(long userId) {
    ensureRow(userId);
    UserCredits credits = userCreditsRepository.findForUpdateByUserId(userId);
    if (credits == null || credits.getBalance() < 1) {
      log.warn("Credit consume rejected userId={} reason=insufficient_credits", userId);
      return false;
    }
    int previousBalance = credits.getBalance();
    int newBalance = previousBalance - 1;
    credits.setBalance(newBalance);
    credits.setUpdatedAt(Instant.now());
    log.info(
        "Credit consumed userId={} amount=1 previousBalance={} newBalance={}",
        userId,
        previousBalance,
        newBalance);
    return true;
  }
}
