package com.harshaandra.helix.service.dto;

import com.harshaandra.helix.domain.model.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Claim-facing DTOs. Entities are never returned from a service method: a JPA entity carries
 * lazy proxies and an identity tied to a persistence context, and serialising one straight to
 * JSON is how you get LazyInitializationException in production and accidental field exposure
 * in a security review.
 *
 * Grouped as nested records because they form one cohesive contract; splitting a dozen
 * three-line records across a dozen files makes the contract harder to read, not easier.
 */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record Summary(
            UUID id,
            String claimNumber,
            String policyNumber,
            String claimantName,
            ClaimStatus status,
            BigDecimal totalAmount,
            LocalDate incidentDate,
            Instant submittedAt,
            String assignedAdjuster,
            int lineCount,
            int version
    ) {
    }

    public record Detail(
            UUID id,
            String claimNumber,
            String policyNumber,
            String claimantName,
            ClaimStatus status,
            BigDecimal totalAmount,
            BigDecimal approvedAmount,
            LocalDate incidentDate,
            Instant submittedAt,
            Instant closedAt,
            String assignedAdjuster,
            String description,
            String lossType,
            int lineCount,
            int version,
            PolicyDtos.Summary policy,
            PartyDtos.Claimant claimant,
            PartyDtos.Adjuster adjuster,
            List<Line> lines,
            List<DocumentRef> documents
    ) {
    }

    public record Line(
            UUID id,
            int lineNumber,
            String coverageCode,
            String description,
            BigDecimal claimedAmount,
            BigDecimal approvedAmount,
            String status
    ) {
    }

    public record DocumentRef(
            UUID id,
            String fileName,
            String contentType,
            long sizeBytes,
            Instant uploadedAt
    ) {
    }
}
