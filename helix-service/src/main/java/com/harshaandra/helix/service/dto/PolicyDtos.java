package com.harshaandra.helix.service.dto;

import com.harshaandra.helix.domain.model.PolicyStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record Summary(
            UUID id,
            String policyNumber,
            String productName,
            String holderName,
            PolicyStatus status,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            BigDecimal premiumAmount,
            int coverageCount
    ) {
    }

    public record Detail(
            UUID id,
            String policyNumber,
            String productName,
            String holderName,
            PolicyStatus status,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            BigDecimal premiumAmount,
            int coverageCount,
            List<Coverage> coverages
    ) {
    }

    public record Coverage(
            UUID id,
            String code,
            String name,
            BigDecimal limitAmount,
            BigDecimal deductibleAmount
    ) {
    }
}
