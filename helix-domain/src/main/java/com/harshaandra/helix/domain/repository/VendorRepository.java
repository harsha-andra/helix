package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    List<Vendor> findByActiveTrueOrderByName();
}
