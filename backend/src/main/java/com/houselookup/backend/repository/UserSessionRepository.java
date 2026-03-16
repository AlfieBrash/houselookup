package com.houselookup.backend.repository;

import com.houselookup.backend.model.UserSession;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
  Optional<UserSession> findBySessionTokenHash(String sessionTokenHash);

  @Modifying
  @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
  int deleteExpired(@NotNull Instant now);

  @Modifying
  @Query("DELETE FROM UserSession s WHERE s.user.id = :userId")
  int deleteByUserId(long userId);

  List<UserSession> findByUser_Id(long userId);
}
