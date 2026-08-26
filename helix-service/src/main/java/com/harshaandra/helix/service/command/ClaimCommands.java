package com.harshaandra.helix.service.command;

import com.harshaandra.helix.domain.model.ClaimStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inbound commands. Validation lives on the command, not in the controller, so the SOAP
 * endpoint and the REST controller get the identical rules rather than each re-implementing
 * them and drifting apart.
 */
public final class ClaimCommands {

    private ClaimCommands() {
    }

    public record CreateClaim(
            @NotBlank String policyNumber,
            @NotNull UUID claimantId,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @Size(max = 2000) String description,
            @Size(max = 40) String lossType,
            @NotEmpty @Valid List<CreateClaimLine> lines
    ) {
    }

    public record CreateClaimLine(
            @NotBlank @Size(max = 24) String coverageCode,
            @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.00", inclusive = false)
            @Digits(integer = 13, fraction = 2) BigDecimal claimedAmount
    ) {
    }

    /**
     * The client echoes back the version it read. If another adjuster has saved in the meantime
     * the versions differ and the update is rejected — see ADR 0003.
     */
    public record ChangeStatus(
            @NotNull ClaimStatus status,
            @NotNull @Min(0) Integer version,
            @Size(max = 500) String reason
    ) {
    }
}
