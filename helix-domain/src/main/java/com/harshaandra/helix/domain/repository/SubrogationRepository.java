package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Subrogation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubrogationRepository extends JpaRepository<Subrogation, UUID> {
    List<Subrogation> findByClaimId(UUID claimId);
}
