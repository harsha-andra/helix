package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail. Deliberately has no setters and no updatable columns: an audit row
 * is written once and never mutated. Anything that needs to "correct" an audit entry writes a
 * new compensating entry instead, which is what auditors actually expect to see.
 */
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at")
})
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "actor", nullable = false, updatable = false, length = 160)
    private String actor;

    @Column(name = "detail", updatable = false, length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    private AuditEvent(String entityType, UUID entityId, String action, String actor, String detail) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public static AuditEvent of(String entityType, UUID entityId, String action, String actor, String detail) {
        return new AuditEvent(entityType, entityId, action, actor, detail);
    }
}
