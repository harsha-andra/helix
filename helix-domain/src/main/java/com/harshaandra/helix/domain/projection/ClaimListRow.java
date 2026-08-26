package com.harshaandra.helix.domain.projection;

import com.harshaandra.helix.domain.model.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat row for the claims list.
 *
 * A list screen does not need entities. It needs twelve scalars per row, and the moment you hand
 * it managed entities you have signed up for lazy loading, a persistence context full of objects
 * nobody will mutate, and a dirty-checking pass at flush time over all of them.
 *
 * Selecting straight into this record means the association columns are joined in SQL and the
 * line count is computed as a scalar subquery, so the whole page is one statement no matter how
 * many rows it contains. See docs/ARCHITECTURE.md for the measured numbers.
 */
public record ClaimListRow(
        UUID id,
        String claimNumber,
        String policyNumber,
        String claimantFirstName,
        String claimantLastName,
        ClaimStatus status,
        BigDecimal totalAmount,
        LocalDate incidentDate,
        Instant submittedAt,
        String assignedAdjuster,
        int lineCount,
        int version
) {
    public String claimantName() {
        return claimantFirstName + " " + claimantLastName;
    }
}
