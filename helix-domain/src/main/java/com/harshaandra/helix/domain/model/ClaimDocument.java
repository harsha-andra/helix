package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_document", indexes = {
        @Index(name = "idx_document_claim", columnList = "claim_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClaimDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "document_type", length = 40)
    private String documentType;

    /** Object-store key. Binary content never lives in Postgres. */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by", length = 160)
    private String uploadedBy;
}
