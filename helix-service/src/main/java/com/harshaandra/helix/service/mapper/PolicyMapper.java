package com.harshaandra.helix.service.mapper;

import com.harshaandra.helix.domain.model.Coverage;
import com.harshaandra.helix.domain.model.Policy;
import com.harshaandra.helix.service.dto.PolicyDtos;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PolicyMapper {

    default PolicyDtos.Summary toSummary(Policy policy) {
        return PolicyMapperHelper.summary(policy);
    }

    List<PolicyDtos.Summary> toSummaries(List<Policy> policies);

    PolicyDtos.Coverage toCoverage(Coverage coverage);

    List<PolicyDtos.Coverage> toCoverages(List<Coverage> coverages);

    default PolicyDtos.Detail toDetail(Policy policy) {
        if (policy == null) {
            return null;
        }
        PolicyDtos.Summary summary = PolicyMapperHelper.summary(policy);
        return new PolicyDtos.Detail(
                summary.id(), summary.policyNumber(), summary.productName(), summary.holderName(),
                summary.status(), summary.effectiveDate(), summary.expirationDate(),
                summary.premiumAmount(), summary.coverageCount(),
                toCoverages(policy.getCoverages())
        );
    }
}
