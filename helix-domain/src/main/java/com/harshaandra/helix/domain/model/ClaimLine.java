package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "claim_line", indexes = {
        @Index(name = "idx_claim_line_claim", columnList = "claim_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coverage_id")
    private Coverage coverage;

    @Column(name = "coverage_code", nullable = false, length = 24)
    private String coverageCode;

    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "claimed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimedAmount = BigDecimal.ZERO;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClaimLineStatus status = ClaimLineStatus.PENDING;
}
