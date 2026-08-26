package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.ClaimNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimNoteRepository extends JpaRepository<ClaimNote, UUID> {
    List<ClaimNote> findByClaimIdOrderByCreatedAtDesc(UUID claimId);
}
