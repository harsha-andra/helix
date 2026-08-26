package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "beneficiary", indexes = {
        @Index(name = "idx_beneficiary_policy", columnList = "policy_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "relationship", length = 40)
    private String relationship;

    /** Percentage share; the service layer enforces that a policy's shares sum to 100. */
    @Column(name = "share_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal sharePercent = BigDecimal.ZERO;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "primary_beneficiary", nullable = false)
    private boolean primaryBeneficiary = true;
}
