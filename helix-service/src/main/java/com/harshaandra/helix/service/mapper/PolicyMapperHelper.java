package com.harshaandra.helix.service.mapper;

import com.harshaandra.helix.domain.model.Policy;
import com.harshaandra.helix.service.dto.PolicyDtos;

/**
 * Plain static mapping for Policy → summary. Kept out of the MapStruct interface because the
 * holder name and coverage count both need a null-safe read of a lazy association, and an
 * explicit method is easier to reason about than three @Mapping annotations doing the same
 * thing implicitly.
 */
final class PolicyMapperHelper {

    private PolicyMapperHelper() {
    }

    static PolicyDtos.Summary summary(Policy policy) {
        if (policy == null) {
            return null;
        }
        return new PolicyDtos.Summary(
                policy.getId(),
                policy.getPolicyNumber(),
                policy.getProductName(),
                policy.getHolder() == null ? null : policy.getHolder().getFullName(),
                policy.getStatus(),
                policy.getEffectiveDate(),
                policy.getExpirationDate(),
                policy.getPremiumAmount(),
                policy.getCoverages() == null ? 0 : policy.getCoverages().size()
        );
    }
}
