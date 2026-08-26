package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    /**
     * `term` must be an empty string, never null, when no search is active.
     *
     * PostgreSQL infers the type of an untyped JDBC null as bytea, so a null parameter here makes
     * the driver ask for lower(bytea), which does not exist — the statement fails outright and
     * the policies list returns 500. An empty term makes the predicate LIKE '%%', which matches
     * every row and keeps one query plan for both the filtered and unfiltered cases.
     *
     * The same mistake was made twice in this repository layer: see ClaimRepository#findListRows.
     */
    @EntityGraph(attributePaths = {"holder", "product"})
    @Query("""
            select p from Policy p
            where lower(p.policyNumber) like lower(concat('%', :term, '%'))
               or lower(p.holder.lastName) like lower(concat('%', :term, '%'))
            """)
    Page<Policy> search(@Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = {"holder", "product", "coverages"})
    Optional<Policy> findWithCoveragesById(UUID id);

    @EntityGraph(attributePaths = {"coverages"})
    Optional<Policy> findWithCoveragesByPolicyNumber(String policyNumber);
}
