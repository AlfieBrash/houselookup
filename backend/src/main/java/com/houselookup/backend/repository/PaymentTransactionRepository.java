package com.houselookup.backend.repository;

import com.houselookup.backend.model.PaymentTransaction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  Optional<PaymentTransaction> findByProviderSessionId(String providerSessionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT tx FROM PaymentTransaction tx WHERE tx.providerSessionId = :providerSessionId")
  Optional<PaymentTransaction> findForUpdateByProviderSessionId(String providerSessionId);
}
