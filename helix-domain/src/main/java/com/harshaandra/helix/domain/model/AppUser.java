package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Local projection of an identity-provider subject. Authentication happens at the IdP (Keycloak
 * locally, Entra ID in Azure) — this table exists so claims can reference an actor by a stable
 * internal id and so role grants are auditable. No password is ever stored here.
 */
@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_app_user_subject", columnList = "subject", unique = true),
        @Index(name = "idx_app_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The `sub` claim from the OAuth2 token. */
    @Column(name = "subject", nullable = false, unique = true, length = 160)
    private String subject;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            indexes = @Index(name = "idx_user_role_user", columnList = "user_id"))
    @Column(name = "role", nullable = false, length = 40)
    private Set<String> roles = new HashSet<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
