package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "policy", indexes = {
        @Index(name = "idx_policy_number", columnList = "policy_number", unique = true),
        @Index(name = "idx_policy_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "policy_number", nullable = false, unique = true, length = 32)
    private String policyNumber;

    @NotBlank
    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @NotNull
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @NotNull
    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @NotNull
    @Column(name = "premium_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal premiumAmount = BigDecimal.ZERO;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private Claimant holder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InsuredAsset> insuredAssets = new ArrayList<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Beneficiary> beneficiaries = new ArrayList<>();

    /**
     * Coverages are owned by the policy: they have no independent lifecycle, so cascade-all
     * plus orphan removal is correct here. Removing a coverage from this list deletes the row.
     */
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Coverage> coverages = new ArrayList<>();

    public void addCoverage(Coverage coverage) {
        coverages.add(coverage);
        coverage.setPolicy(this);
    }

    public void removeCoverage(Coverage coverage) {
        coverages.remove(coverage);
        coverage.setPolicy(null);
    }

    public boolean coversDate(LocalDate date) {
        return !date.isBefore(effectiveDate) && !date.isAfter(expirationDate);
    }
}
