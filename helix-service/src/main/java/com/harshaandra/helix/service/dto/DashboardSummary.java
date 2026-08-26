package com.harshaandra.helix.service.dto;

import com.harshaandra.helix.domain.model.ClaimStatus;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummary(
        long openClaims,
        long awaitingReview,
        BigDecimal totalReservedAmount,
        double avgCycleTimeDays,
        List<StatusCount> byStatus,
        List<PartyDtos.AuditEntry> recentActivity
) {
    public record StatusCount(ClaimStatus status, long count) {
    }
}
