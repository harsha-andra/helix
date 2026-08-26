package com.harshaandra.helix.api.rest;

import com.harshaandra.helix.service.DashboardService;
import com.harshaandra.helix.service.DirectoryService;
import com.harshaandra.helix.service.PolicyService;
import com.harshaandra.helix.service.dto.DashboardSummary;
import com.harshaandra.helix.service.dto.PartyDtos;
import com.harshaandra.helix.service.dto.PolicyDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference data", description = "Policies, parties and dashboard aggregates")
public class CatalogController {

    private final PolicyService policyService;
    private final DirectoryService directoryService;
    private final DashboardService dashboardService;

    public CatalogController(PolicyService policyService,
                             DirectoryService directoryService,
                             DashboardService dashboardService) {
        this.policyService = policyService;
        this.directoryService = directoryService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/policies")
    @Operation(summary = "Search policies")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public PagedResponse<PolicyDtos.Summary> policies(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25, sort = "policyNumber") Pageable pageable) {
        return PagedResponse.from(policyService.search(q, pageable));
    }

    @GetMapping("/policies/{id}")
    @Operation(summary = "Get a policy with its coverages")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public PolicyDtos.Detail policy(@PathVariable UUID id) {
        return policyService.get(id);
    }

    /**
     * Typeahead endpoint. The client cancels superseded requests with switchMap; the server
     * additionally caps the result set, because the client is not a trust boundary.
     */
    @GetMapping("/claimants/search")
    @Operation(summary = "Typeahead search for claimants (max 10 results)")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public List<PartyDtos.Claimant> searchClaimants(@RequestParam("q") String term) {
        return directoryService.searchClaimants(term);
    }

    @GetMapping("/adjusters")
    @Operation(summary = "Active adjusters with their open-claim counts")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR')")
    public List<PartyDtos.Adjuster> adjusters() {
        return directoryService.listAdjusters();
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Dashboard aggregates")
    @PreAuthorize("hasAnyRole('ADJUSTER','SUPERVISOR','READONLY')")
    public DashboardSummary dashboard() {
        return dashboardService.summary();
    }
}
