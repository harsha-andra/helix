package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Money an insurer sets aside against an open claim. Reserves are re-estimated over the life
 * of the claim, so history is kept as separate rows rather than mutating a single amount.
 */
@Entity
@Table(name = "reserve", indexes = {
        @Index(name = "idx_reserve_claim", columnList = "claim_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Reserve {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "reserve_type", nullable = false, length = 24)
    private String reserveType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "previous_amount", precision = 15, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "set_by", nullable = false, length = 160)
    private String setBy;

    @Column(name = "rationale", length = 1000)
    private String rationale;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt = Instant.now();

    @Column(name = "superseded", nullable = false)
    private boolean superseded = false;
}
