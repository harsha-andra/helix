package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_claim", columnList = "claim_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_reference", columnList = "reference", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_vendor_id")
    private Vendor payeeVendor;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "method", length = 24)
    private String method;

    /** Idempotency handle for the downstream disbursement system. */
    @Column(name = "reference", nullable = false, unique = true, length = 64)
    private String reference;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
