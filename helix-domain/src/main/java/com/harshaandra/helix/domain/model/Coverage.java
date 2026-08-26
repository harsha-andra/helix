package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "coverage", indexes = {
        @Index(name = "idx_coverage_policy", columnList = "policy_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Coverage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "code", nullable = false, length = 24)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount = BigDecimal.ZERO;

    @NotNull
    @Column(name = "deductible_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductibleAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;
}
