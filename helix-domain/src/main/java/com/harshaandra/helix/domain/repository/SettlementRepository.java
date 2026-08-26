package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    Optional<Settlement> findByClaimId(UUID claimId);
}
