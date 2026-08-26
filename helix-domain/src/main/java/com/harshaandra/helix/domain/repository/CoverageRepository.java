package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Coverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverageRepository extends JpaRepository<Coverage, UUID> {
    List<Coverage> findByPolicyId(UUID policyId);
}
