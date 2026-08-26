package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Repair shops, medical providers, independent adjusters, salvage yards. */
@Entity
@Table(name = "vendor", indexes = {
        @Index(name = "idx_vendor_tax_id", columnList = "tax_id", unique = true),
        @Index(name = "idx_vendor_type", columnList = "vendor_type")
})
@Getter
@Setter
@NoArgsConstructor
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "vendor_type", nullable = false, length = 32)
    private String vendorType;

    @Column(name = "tax_id", unique = true, length = 32)
    private String taxId;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "preferred", nullable = false)
    private boolean preferred = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
