package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The thing being insured. Single table with a discriminator column rather than a class
 * hierarchy: vehicles and dwellings share most attributes and queries almost always filter
 * across both, so joined-table inheritance would buy nothing but joins.
 */
@Entity
@Table(name = "insured_asset", indexes = {
        @Index(name = "idx_asset_policy", columnList = "policy_id"),
        @Index(name = "idx_asset_identifier", columnList = "identifier")
})
@Getter
@Setter
@NoArgsConstructor
public class InsuredAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "asset_type", nullable = false, length = 24)
    private String assetType;

    /** VIN for a vehicle, parcel/APN for a dwelling. */
    @Column(name = "identifier", length = 64)
    private String identifier;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Column(name = "insured_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal insuredValue = BigDecimal.ZERO;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "postal_code", length = 12)
    private String postalCode;
}
