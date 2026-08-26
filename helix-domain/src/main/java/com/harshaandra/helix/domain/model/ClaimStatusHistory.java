package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per status transition. Separate from audit_event because cycle-time analytics query
 * this constantly and should not scan a polymorphic audit table to do it.
 */
@Entity
@Table(name = "claim_status_history", indexes = {
        @Index(name = "idx_status_history_claim", columnList = "claim_id"),
        @Index(name = "idx_status_history_changed_at", columnList = "changed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private ClaimStatus toStatus;

    @Column(name = "changed_by", nullable = false, length = 160)
    private String changedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}
