package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_note", indexes = {
        @Index(name = "idx_note_claim", columnList = "claim_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClaimNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "author", nullable = false, length = 160)
    private String author;

    /** Internal notes are never serialised to claimant-facing responses. */
    @Column(name = "internal_only", nullable = false)
    private boolean internalOnly = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
