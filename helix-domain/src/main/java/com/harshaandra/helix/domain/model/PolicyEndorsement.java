package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A mid-term change to a policy — added driver, raised limit, changed address. */
@Entity
@Table(name = "policy_endorsement", indexes = {
        @Index(name = "idx_endorsement_policy", columnList = "policy_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PolicyEndorsement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "endorsement_number", nullable = false, length = 32)
    private String endorsementNumber;

    @Column(name = "endorsement_type", nullable = false, length = 40)
    private String endorsementType;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "premium_delta", nullable = false, precision = 15, scale = 2)
    private BigDecimal premiumDelta = BigDecimal.ZERO;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 160)
    private String createdBy;
}
