package com.houselookup.backend.repository;

import com.houselookup.backend.model.UserCredits;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface UserCreditsRepository extends JpaRepository<UserCredits, Long> {
  UserCredits findByUserId(long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM UserCredits c WHERE c.user.id = :userId")
  UserCredits findForUpdateByUserId(@NotNull Long userId);
}
