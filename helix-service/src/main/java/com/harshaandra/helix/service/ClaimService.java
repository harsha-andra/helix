package com.harshaandra.helix.service;

import com.harshaandra.helix.domain.model.*;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.domain.repository.ClaimantRepository;
import com.harshaandra.helix.domain.repository.PolicyRepository;
import com.harshaandra.helix.domain.repository.AuditEventRepository;
import com.harshaandra.helix.domain.repository.ClaimStatusHistoryRepository;
import com.harshaandra.helix.service.command.ClaimCommands;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.dto.PartyDtos;
import com.harshaandra.helix.service.exception.ServiceExceptions.*;
import com.harshaandra.helix.service.mapper.ClaimMapper;
import com.harshaandra.helix.service.mapper.PartyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The transactional boundary for everything claim-related.
 *
 * @Transactional lives here and nowhere else. Not on repositories (too fine-grained — each call
 * would be its own transaction and a multi-step operation could half-commit) and not on
 * controllers (too coarse — an open transaction would span view rendering and HTTP serialisation).
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final ClaimantRepository claimantRepository;
    private final AuditEventRepository auditRepository;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final ClaimMapper claimMapper;
    private final PartyMapper partyMapper;

    public ClaimService(ClaimRepository claimRepository,
                        PolicyRepository policyRepository,
                        ClaimantRepository claimantRepository,
                        AuditEventRepository auditRepository,
                        ClaimStatusHistoryRepository statusHistoryRepository,
                        ClaimMapper claimMapper,
                        PartyMapper partyMapper) {
        this.claimRepository = claimRepository;
        this.policyRepository = policyRepository;
        this.claimantRepository = claimantRepository;
        this.auditRepository = auditRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.claimMapper = claimMapper;
        this.partyMapper = partyMapper;
    }

    // ---------------------------------------------------------------- reads

    /**
     * The list endpoint. Backed by a scalar projection, so it is exactly 2 statements (the page
     * and the count) no matter how large the page is.
     *
     * {@link ClaimRepository#findAllNaive} is the same read done wrong and is kept so
     * ClaimFetchStrategyTest can assert the difference rather than assert a comment.
     */
    @Transactional(readOnly = true)
    public Page<ClaimDtos.Summary> list(ClaimStatus status, String term, Pageable pageable) {
        return claimRepository.findListRows(status, normaliseTerm(term), pageable)
                .map(row -> new ClaimDtos.Summary(
                        row.id(), row.claimNumber(), row.policyNumber(), row.claimantName(),
                        row.status(), row.totalAmount(), row.incidentDate(), row.submittedAt(),
                        row.assignedAdjuster(), row.lineCount(), row.version()));
    }

    @Transactional(readOnly = true)
    public ClaimDtos.Detail get(UUID id) {
        Claim claim = claimRepository.findWithDetailById(id)
                .orElseThrow(() -> new NotFoundException("Claim", id.toString()));
        return claimMapper.toDetail(claim);
    }

    @Transactional(readOnly = true)
    public ClaimDtos.Detail getByNumber(String claimNumber) {
        Claim claim = claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new NotFoundException("Claim", claimNumber));
        return claimMapper.toDetail(claim);
    }

    @Transactional(readOnly = true)
    public List<PartyDtos.AuditEntry> auditTrail(UUID claimId) {
        if (!claimRepository.existsById(claimId)) {
            throw new NotFoundException("Claim", claimId.toString());
        }
        return partyMapper.toAuditEntries(
                auditRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("Claim", claimId));
    }

    // ---------------------------------------------------------------- writes

    @Transactional
    public ClaimDtos.Detail create(ClaimCommands.CreateClaim command, String actor) {
        Policy policy = policyRepository.findWithCoveragesByPolicyNumber(command.policyNumber())
                .orElseThrow(() -> new NotFoundException("Policy", command.policyNumber()));

        if (!policy.getStatus().allowsNewClaims()) {
            throw new BusinessRuleException("POLICY_NOT_ACTIVE",
                    "Policy " + policy.getPolicyNumber() + " is " + policy.getStatus()
                            + " and cannot accept new claims");
        }
        if (!policy.coversDate(command.incidentDate())) {
            throw new BusinessRuleException("INCIDENT_OUTSIDE_POLICY_TERM",
                    "The incident date " + command.incidentDate() + " falls outside the policy term ("
                            + policy.getEffectiveDate() + " to " + policy.getExpirationDate() + ")");
        }

        Claimant claimant = claimantRepository.findById(command.claimantId())
                .orElseThrow(() -> new NotFoundException("Claimant", command.claimantId().toString()));

        Claim claim = new Claim();
        claim.setClaimNumber(nextClaimNumber());
        claim.setPolicy(policy);
        claim.setClaimant(claimant);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(command.incidentDate());
        claim.setDescription(command.description());
        claim.setLossType(command.lossType());

        int lineNumber = 1;
        for (ClaimCommands.CreateClaimLine line : command.lines()) {
            Coverage coverage = policy.getCoverages().stream()
                    .filter(c -> c.getCode().equalsIgnoreCase(line.coverageCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("COVERAGE_NOT_ON_POLICY",
                            "Coverage '" + line.coverageCode() + "' is not part of policy "
                                    + policy.getPolicyNumber()));

            ClaimLine claimLine = new ClaimLine();
            claimLine.setLineNumber(lineNumber++);
            claimLine.setCoverage(coverage);
            claimLine.setCoverageCode(coverage.getCode());
            claimLine.setDescription(line.description());
            claimLine.setClaimedAmount(line.claimedAmount());
            claimLine.setStatus(ClaimLineStatus.PENDING);
            claim.addLine(claimLine);
        }

        Claim saved = claimRepository.save(claim);
        recordAudit(saved, "CREATED", actor, "Claim opened with " + saved.getLines().size() + " line(s)");
        recordStatusChange(saved, null, ClaimStatus.SUBMITTED, actor, "Initial submission");

        log.info("Created claim {} against policy {} for {}",
                saved.getClaimNumber(), policy.getPolicyNumber(), claimant.getFullName());
        return claimMapper.toDetail(saved);
    }

    /**
     * Status change with an explicit optimistic-lock check.
     *
     * The @Version column already protects against a lost update at flush time, but by then
     * Hibernate throws an ObjectOptimisticLockingFailureException that carries no useful
     * information for the caller. Comparing the client-supplied version up front lets us return
     * a 409 that actually says which version the caller had and which one is current, so the UI
     * can tell the user what happened instead of showing "something went wrong".
     */
    @Transactional
    public ClaimDtos.Detail changeStatus(UUID id, ClaimCommands.ChangeStatus command, String actor) {
        Claim claim = claimRepository.findWithDetailById(id)
                .orElseThrow(() -> new NotFoundException("Claim", id.toString()));

        if (claim.getVersion() != command.version()) {
            throw new StaleClaimException(command.version(), claim.getVersion());
        }

        ClaimStatus from = claim.getStatus();
        ClaimStatus to = command.status();
        if (!from.canTransitionTo(to)) {
            throw new IllegalStatusTransitionException(from, to);
        }

        claim.setStatus(to);
        if (to.isTerminal()) {
            claim.setClosedAt(java.time.Instant.now());
        }
        if (to == ClaimStatus.APPROVED || to == ClaimStatus.PARTIALLY_APPROVED) {
            claim.setApprovedAmount(approvedTotal(claim));
        }

        recordStatusChange(claim, from, to, actor, command.reason());
        recordAudit(claim, "STATUS_CHANGED", actor, from + " -> " + to
                + (command.reason() == null ? "" : " (" + command.reason() + ")"));

        return claimMapper.toDetail(claim);
    }

    @Transactional
    public ClaimDtos.Detail assignAdjuster(UUID claimId, Adjuster adjuster, String actor) {
        Claim claim = claimRepository.findWithDetailById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId.toString()));
        claim.setAdjuster(adjuster);
        recordAudit(claim, "ASSIGNED", actor, "Assigned to " + adjuster.getName());
        return claimMapper.toDetail(claim);
    }

    // ---------------------------------------------------------------- internals

    /**
     * Deliberately package-private and called only from within an already-open transaction.
     * Note that it is NOT annotated: an annotation here would be a lie, because a call from
     * another method of this class bypasses the proxy entirely. See ADR 0002.
     */
    void recordAudit(Claim claim, String action, String actor, String detail) {
        auditRepository.save(AuditEvent.of("Claim", claim.getId(), action, actor, detail));
    }

    void recordStatusChange(Claim claim, ClaimStatus from, ClaimStatus to, String actor, String reason) {
        ClaimStatusHistory history = new ClaimStatusHistory();
        history.setClaim(claim);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(actor);
        history.setReason(reason);
        statusHistoryRepository.save(history);
    }

    private BigDecimal approvedTotal(Claim claim) {
        return claim.getLines().stream()
                .map(line -> line.getApprovedAmount() == null ? line.getClaimedAmount() : line.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nextClaimNumber() {
        // Readable, sortable, and unique enough for a demo. A production system would take this
        // from a database sequence so two application instances cannot collide.
        return "CLM-%d-%06d".formatted(LocalDate.now().getYear(),
                ThreadLocalRandom.current().nextInt(1, 999_999));
    }

    /** Empty, never null — see the note on ClaimRepository#findListRows. */
    private static String normaliseTerm(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    /**
     * Used by the async adjudication listener, which already runs outside any transaction and
     * must start its own. REQUIRES_NEW rather than the default so that a failure adjudicating
     * one message cannot roll back a batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAdjudicated(UUID claimId, boolean approved, String actor) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId.toString()));
        ClaimStatus target = approved ? ClaimStatus.APPROVED : ClaimStatus.DENIED;
        if (claim.getStatus().canTransitionTo(target)) {
            ClaimStatus from = claim.getStatus();
            claim.setStatus(target);
            recordStatusChange(claim, from, target, actor, "Automated adjudication");
            recordAudit(claim, "ADJUDICATED", actor, "Automated adjudication result: " + target);
        }
    }
}
