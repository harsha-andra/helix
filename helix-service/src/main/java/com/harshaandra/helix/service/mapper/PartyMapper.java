package com.harshaandra.helix.service.mapper;

import com.harshaandra.helix.domain.model.Adjuster;
import com.harshaandra.helix.domain.model.AuditEvent;
import com.harshaandra.helix.domain.model.Claimant;
import com.harshaandra.helix.service.dto.PartyDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PartyMapper {

    PartyDtos.Claimant toClaimant(Claimant claimant);

    List<PartyDtos.Claimant> toClaimants(List<Claimant> claimants);

    @Mapping(target = "activeClaims", constant = "0L")
    PartyDtos.Adjuster toAdjuster(Adjuster adjuster);

    List<PartyDtos.Adjuster> toAdjusters(List<Adjuster> adjusters);

    PartyDtos.AuditEntry toAuditEntry(AuditEvent event);

    List<PartyDtos.AuditEntry> toAuditEntries(List<AuditEvent> events);
}
