package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByPolicyId(UUID policyId);
}
