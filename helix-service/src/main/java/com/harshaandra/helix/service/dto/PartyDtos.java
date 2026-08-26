package com.harshaandra.helix.service.dto;

import java.time.Instant;
import java.util.UUID;

public final class PartyDtos {

    private PartyDtos() {
    }

    public record Claimant(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String city,
            String state
    ) {
    }

    public record Adjuster(
            UUID id,
            String name,
            String email,
            long activeClaims
    ) {
    }

    public record AuditEntry(
            UUID id,
            String entityType,
            UUID entityId,
            String action,
            String actor,
            Instant occurredAt,
            String detail
    ) {
    }
}
