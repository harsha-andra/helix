package com.harshaandra.helix.api.rest;

import com.harshaandra.helix.domain.model.ClaimStatus;
import com.harshaandra.helix.service.ClaimService;
import com.harshaandra.helix.service.command.ClaimCommands;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.dto.PartyDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims", description = "Claim intake, review and adjudication")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    @Operation(summary = "List claims",
            description = "Paged claim summaries. Backed by an entity-graph query: 2 SQL "
                    + "statements regardless of page size.")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public PagedResponse<ClaimDtos.Summary> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25, sort = "submittedAt") Pageable pageable) {
        return PagedResponse.from(claimService.list(status, q, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one claim with its lines, documents and policy")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public ClaimDtos.Detail get(@PathVariable UUID id) {
        return claimService.get(id);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Audit trail for a claim")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR')")
    public List<PartyDtos.AuditEntry> audit(@PathVariable UUID id) {
        return claimService.auditTrail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new claim")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Claim created"),
            @ApiResponse(responseCode = "422", description = "A business rule rejected the claim"),
            @ApiResponse(responseCode = "404", description = "Policy or claimant not found")
    })
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR')")
    public ResponseEntity<ClaimDtos.Detail> create(@Valid @RequestBody ClaimCommands.CreateClaim command,
                                                   @AuthenticationPrincipal Jwt principal,
                                                   UriComponentsBuilder uriBuilder) {
        ClaimDtos.Detail created = claimService.create(command, actorOf(principal));
        URI location = uriBuilder.path("/api/v1/claims/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * PATCH rather than PUT: this changes one field, and the caller must send the version it
     * read so a concurrent edit is rejected rather than silently overwritten.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Change claim status",
            description = "Requires the version the client last read. Returns 409 if another "
                    + "adjuster has saved since.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "409", description = "Someone else modified this claim"),
            @ApiResponse(responseCode = "422", description = "Illegal status transition")
    })
    @PreAuthorize("hasRole('ADJUSTER')")
    public ClaimDtos.Detail changeStatus(@PathVariable UUID id,
                                         @Valid @RequestBody ClaimCommands.ChangeStatus command,
                                         @AuthenticationPrincipal Jwt principal) {
        return claimService.changeStatus(id, command, actorOf(principal));
    }

    private static String actorOf(Jwt principal) {
        if (principal == null) {
            return "anonymous";
        }
        String preferred = principal.getClaimAsString("preferred_username");
        return preferred != null ? preferred : principal.getSubject();
    }
}
