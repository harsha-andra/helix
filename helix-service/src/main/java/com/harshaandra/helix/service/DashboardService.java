package com.harshaandra.helix.service;

import com.harshaandra.helix.domain.model.Claim;
import com.harshaandra.helix.domain.model.ClaimStatus;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.domain.repository.AuditEventRepository;
import com.harshaandra.helix.service.dto.DashboardSummary;
import com.harshaandra.helix.service.mapper.PartyMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final ClaimRepository claimRepository;
    private final AuditEventRepository auditRepository;
    private final PartyMapper partyMapper;

    public DashboardService(ClaimRepository claimRepository,
                            AuditEventRepository auditRepository,
                            PartyMapper partyMapper) {
        this.claimRepository = claimRepository;
        this.auditRepository = auditRepository;
        this.partyMapper = partyMapper;
    }

    /**
     * Every figure here comes from an aggregate query or a bounded page. None of it loads a
     * collection of claims into memory to count them — a dashboard that gets slower as the
     * business grows is a dashboard that gets switched off.
     */
    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        claimRepository.countGroupedByStatus()
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));

        long open = counts.entrySet().stream()
                .filter(e -> !e.getKey().isTerminal())
                .mapToLong(Map.Entry::getValue)
                .sum();

        BigDecimal reserved = claimRepository.sumOpenReserves();

        List<DashboardSummary.StatusCount> byStatus = counts.entrySet().stream()
                .map(e -> new DashboardSummary.StatusCount(e.getKey(), e.getValue()))
                .toList();

        return new DashboardSummary(
                open,
                counts.getOrDefault(ClaimStatus.UNDER_REVIEW, 0L),
                reserved == null ? BigDecimal.ZERO : reserved,
                averageCycleTimeDays(),
                byStatus,
                partyMapper.toAuditEntries(
                        auditRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, 12)).getContent())
        );
    }

    /**
     * Mean days from submission to closure over the last 90 days of closed claims. Bounded
     * window on purpose: a lifetime average stops moving once the book is large enough to be
     * useless as an operational signal.
     */
    private double averageCycleTimeDays() {
        Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Claim> recent = claimRepository.findSubmittedSince(since);
        return recent.stream()
                .filter(c -> c.getClosedAt() != null)
                .mapToDouble(c -> Duration.between(c.getSubmittedAt(), c.getClosedAt()).toHours() / 24.0)
                .average()
                .orElse(0.0);
    }
}
