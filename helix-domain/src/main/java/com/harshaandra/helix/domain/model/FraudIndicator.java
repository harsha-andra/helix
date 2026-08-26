package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A red flag raised against a claim, either by a rule or by an adjuster. Kept separate from
 * claim so that fraud review can be permissioned independently of ordinary claim access.
 */
@Entity
@Table(name = "fraud_indicator", indexes = {
        @Index(name = "idx_fraud_claim", columnList = "claim_id"),
        @Index(name = "idx_fraud_severity", columnList = "severity")
})
@Getter
@Setter
@NoArgsConstructor
public class FraudIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "raised_by", nullable = false, length = 160)
    private String raisedBy;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt = Instant.now();

    @Column(name = "cleared", nullable = false)
    private boolean cleared = false;

    @Column(name = "cleared_reason", length = 500)
    private String clearedReason;
}
