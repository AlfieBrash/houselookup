package com.houselookup.backend.service;

import com.houselookup.backend.model.User;
import com.houselookup.backend.model.UserCredits;
import com.houselookup.backend.repository.UserCreditsRepository;
import com.houselookup.backend.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {
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
    credits.setBalance(Integer.valueOf(credits.getBalance() + amount));
    credits.setUpdatedAt(Instant.now());
  }

  @Transactional
  public boolean consumeOne(long userId) {
    ensureRow(userId);
    UserCredits credits = userCreditsRepository.findForUpdateByUserId(userId);
    if (credits == null || credits.getBalance() < 1) {
      return false;
    }
    credits.setBalance(credits.getBalance() - 1);
    credits.setUpdatedAt(Instant.now());
    return true;
  }
}
