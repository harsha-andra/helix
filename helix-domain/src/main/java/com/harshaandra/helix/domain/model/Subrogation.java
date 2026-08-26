package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Recovering paid losses from an at-fault third party. */
@Entity
@Table(name = "subrogation", indexes = {
        @Index(name = "idx_subrogation_claim", columnList = "claim_id"),
        @Index(name = "idx_subrogation_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Subrogation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "third_party_name", length = 200)
    private String thirdPartyName;

    @Column(name = "third_party_insurer", length = 200)
    private String thirdPartyInsurer;

    @Column(name = "demand_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal demandAmount = BigDecimal.ZERO;

    @Column(name = "recovered_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;
}
