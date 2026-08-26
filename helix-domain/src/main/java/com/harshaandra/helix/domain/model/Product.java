package com.harshaandra.helix.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Insurance product catalogue entry a policy is written against. */
@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 24)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "line_of_business", nullable = false, length = 40)
    private String lineOfBusiness;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
