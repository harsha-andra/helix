package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** History of which adjuster owned a claim and when — reassignment is common and auditable. */
@Entity
@Table(name = "claim_assignment", indexes = {
        @Index(name = "idx_assignment_claim", columnList = "claim_id"),
        @Index(name = "idx_assignment_adjuster", columnList = "adjuster_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClaimAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjuster_id", nullable = false)
    private Adjuster adjuster;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "assigned_by", nullable = false, length = 160)
    private String assignedBy;

    @Column(name = "reason", length = 300)
    private String reason;

    public boolean isCurrent() {
        return unassignedAt == null;
    }
}
