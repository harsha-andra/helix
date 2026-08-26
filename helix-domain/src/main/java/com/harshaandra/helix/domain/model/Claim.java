package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claim", indexes = {
        @Index(name = "idx_claim_number", columnList = "claim_number", unique = true),
        @Index(name = "idx_claim_status", columnList = "status"),
        @Index(name = "idx_claim_policy", columnList = "policy_id"),
        @Index(name = "idx_claim_claimant", columnList = "claimant_id"),
        @Index(name = "idx_claim_submitted_at", columnList = "submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "claim_number", nullable = false, unique = true, length = 32)
    private String claimNumber;

    /**
     * LAZY on purpose. A claims list renders 25 rows; eagerly hydrating each claim's policy
     * (and through it, its coverages) is exactly how the N+1 in docs/ARCHITECTURE.md happened.
     * Read paths that genuinely need the policy ask for it with an @EntityGraph.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private Claimant claimant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjuster_id")
    private Adjuster adjuster;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @NotNull
    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "loss_type", length = 40)
    private String lossType;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    /**
     * Optimistic locking. Two adjusters opening the same claim is the demo: the second save
     * fails with an OptimisticLockException rather than silently overwriting the first.
     */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ClaimLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ClaimDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ClaimNote> notes = new ArrayList<>();

    public void addLine(ClaimLine line) {
        lines.add(line);
        line.setClaim(this);
        recalculateTotal();
    }

    public void removeLine(ClaimLine line) {
        lines.remove(line);
        line.setClaim(null);
        recalculateTotal();
    }

    public void addDocument(ClaimDocument document) {
        documents.add(document);
        document.setClaim(this);
    }

    public void addNote(ClaimNote note) {
        notes.add(note);
        note.setClaim(this);
    }

    public void recalculateTotal() {
        this.totalAmount = lines.stream()
                .map(ClaimLine::getClaimedAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOpen() {
        return !status.isTerminal();
    }
}
