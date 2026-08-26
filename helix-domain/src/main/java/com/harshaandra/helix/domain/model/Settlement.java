package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The negotiated agreement that closes a claim's indemnity. */
@Entity
@Table(name = "settlement", indexes = {
        @Index(name = "idx_settlement_claim", columnList = "claim_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private Claim claim;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount = BigDecimal.ZERO;

    @Column(name = "deductible_applied", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductibleApplied = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PROPOSED";

    @Column(name = "agreed_at")
    private Instant agreedAt;

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "release_signed", nullable = false)
    private boolean releaseSigned = false;
}
