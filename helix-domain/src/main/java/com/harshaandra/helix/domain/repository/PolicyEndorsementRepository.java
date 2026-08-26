package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.PolicyEndorsement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyEndorsementRepository extends JpaRepository<PolicyEndorsement, UUID> {
    List<PolicyEndorsement> findByPolicyIdOrderByEffectiveDateDesc(UUID policyId);
}
