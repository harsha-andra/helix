package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "adjuster", indexes = {
        @Index(name = "idx_adjuster_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Adjuster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "license_number", length = 32)
    private String licenseNumber;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
