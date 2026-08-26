package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Reserve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReserveRepository extends JpaRepository<Reserve, UUID> {
    List<Reserve> findByClaimIdAndSupersededFalse(UUID claimId);
}
