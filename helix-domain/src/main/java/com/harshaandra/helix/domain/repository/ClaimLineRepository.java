package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.ClaimLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimLineRepository extends JpaRepository<ClaimLine, UUID> {
    List<ClaimLine> findByClaimIdOrderByLineNumber(UUID claimId);
}
