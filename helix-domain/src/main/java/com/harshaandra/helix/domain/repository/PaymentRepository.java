package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByClaimId(UUID claimId);

    boolean existsByReference(String reference);
}
