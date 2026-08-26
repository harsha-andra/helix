package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.ClaimAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimAssignmentRepository extends JpaRepository<ClaimAssignment, UUID> {
    List<ClaimAssignment> findByClaimIdOrderByAssignedAtDesc(UUID claimId);
}
