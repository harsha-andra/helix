package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "claimant", indexes = {
        @Index(name = "idx_claimant_last_name", columnList = "last_name"),
        @Index(name = "idx_claimant_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Claimant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
