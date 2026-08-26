package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proceeds from disposing of a total-loss asset the insurer now owns. */
@Entity
@Table(name = "salvage_recovery", indexes = {
        @Index(name = "idx_salvage_claim", columnList = "claim_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SalvageRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "asset_description", nullable = false, length = 300)
    private String assetDescription;

    @Column(name = "estimated_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedValue = BigDecimal.ZERO;

    @Column(name = "realised_value", precision = 15, scale = 2)
    private BigDecimal realisedValue;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "disposed_at")
    private Instant disposedAt;
}
