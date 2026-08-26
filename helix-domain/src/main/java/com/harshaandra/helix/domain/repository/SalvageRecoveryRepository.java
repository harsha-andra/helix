package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.SalvageRecovery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalvageRecoveryRepository extends JpaRepository<SalvageRecovery, UUID> {
    List<SalvageRecovery> findByClaimId(UUID claimId);
}
