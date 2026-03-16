package com.houselookup.backend.repository;

import com.houselookup.backend.model.ReportDownloadToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ReportDownloadTokenRepository extends JpaRepository<ReportDownloadToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ReportDownloadToken> findByTokenHash(String tokenHash);

  void deleteByExpiresAtBefore(Instant now);
}
