package com.harshaandra.helix.service.mapper;

import com.harshaandra.helix.domain.model.*;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.dto.PartyDtos;
import com.harshaandra.helix.service.dto.PolicyDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Entity to DTO. Every association this mapper reads must already be initialised by the query
 * that loaded the entity — that is precisely the contract the @EntityGraph on the repository
 * exists to satisfy. Reading a lazy association here is what produced the N+1 documented in
 * docs/ARCHITECTURE.md.
 */
@Mapper(componentModel = "spring", uses = {PolicyMapper.class, PartyMapper.class})
public interface ClaimMapper {

    @Mapping(target = "policyNumber", source = "policy.policyNumber")
    @Mapping(target = "claimantName", source = "claimant", qualifiedByName = "claimantName")
    @Mapping(target = "assignedAdjuster", source = "adjuster", qualifiedByName = "adjusterName")
    @Mapping(target = "lineCount", source = "lines", qualifiedByName = "lineCount")
    ClaimDtos.Summary toSummary(Claim claim);

    List<ClaimDtos.Summary> toSummaries(List<Claim> claims);

    @Mapping(target = "policyNumber", source = "policy.policyNumber")
    @Mapping(target = "claimantName", source = "claimant", qualifiedByName = "claimantName")
    @Mapping(target = "assignedAdjuster", source = "adjuster", qualifiedByName = "adjusterName")
    @Mapping(target = "lineCount", source = "lines", qualifiedByName = "lineCount")
    @Mapping(target = "policy", source = "policy")
    @Mapping(target = "claimant", source = "claimant")
    @Mapping(target = "adjuster", source = "adjuster")
    @Mapping(target = "lines", source = "lines")
    @Mapping(target = "documents", source = "documents")
    ClaimDtos.Detail toDetail(Claim claim);

    @Mapping(target = "status", source = "status")
    ClaimDtos.Line toLine(ClaimLine line);

    ClaimDtos.DocumentRef toDocumentRef(ClaimDocument document);

    @Named("claimantName")
    default String claimantName(Claimant claimant) {
        return claimant == null ? null : claimant.getFullName();
    }

    @Named("adjusterName")
    default String adjusterName(Adjuster adjuster) {
        return adjuster == null ? null : adjuster.getName();
    }

    @Named("lineCount")
    default int lineCount(List<ClaimLine> lines) {
        return lines == null ? 0 : lines.size();
    }

    default String claimLineStatus(ClaimLineStatus status) {
        return status == null ? null : status.name();
    }

    // Policy, Claimant and Adjuster are mapped by the delegates declared in `uses` above.
    // Redeclaring them here would give MapStruct two equally good candidates and fail the build.
}
