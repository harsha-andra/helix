package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Claimant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimantRepository extends JpaRepository<Claimant, UUID> {

    Optional<Claimant> findByEmail(String email);

    /**
     * Backs the typeahead. Deliberately capped with a Pageable rather than returning everything:
     * a search-as-you-type endpoint that can return 10,000 rows is a denial-of-service waiting
     * for a user who types a single letter.
     */
    @Query("""
            select c from Claimant c
            where lower(c.firstName) like lower(concat(:term, '%'))
               or lower(c.lastName) like lower(concat(:term, '%'))
               or lower(c.email) like lower(concat(:term, '%'))
            order by c.lastName, c.firstName
            """)
    List<Claimant> searchByPrefix(@Param("term") String term, Pageable pageable);
}
