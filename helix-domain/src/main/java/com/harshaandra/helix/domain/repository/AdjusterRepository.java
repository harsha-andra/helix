package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Adjuster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AdjusterRepository extends JpaRepository<Adjuster, UUID> {

    List<Adjuster> findByActiveTrueOrderByName();

    /**
     * Counted in the database rather than by loading each adjuster's claims. The difference
     * matters on the adjusters screen, which renders every active adjuster at once.
     */
    @Query("""
            select count(c) from Claim c
            where c.adjuster.id = :adjusterId
              and c.status not in (com.harshaandra.helix.domain.model.ClaimStatus.CLOSED,
                                   com.harshaandra.helix.domain.model.ClaimStatus.DENIED,
                                   com.harshaandra.helix.domain.model.ClaimStatus.PAID)
            """)
    long countOpenClaims(@Param("adjusterId") UUID adjusterId);
}
