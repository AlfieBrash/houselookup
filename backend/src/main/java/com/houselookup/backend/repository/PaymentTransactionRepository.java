package com.houselookup.backend.repository;

import com.houselookup.backend.model.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  Optional<PaymentTransaction> findByProviderSessionId(String providerSessionId);
}
