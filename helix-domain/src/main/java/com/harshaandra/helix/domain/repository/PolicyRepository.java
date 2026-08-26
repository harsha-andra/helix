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

    @EntityGraph(attributePaths = {"holder", "product"})
    @Query("""
            select p from Policy p
            where (:term is null or lower(p.policyNumber) like lower(concat('%', :term, '%'))
                   or lower(p.holder.lastName) like lower(concat('%', :term, '%')))
            """)
    Page<Policy> search(@Param("term") String term, Pageable pageable);

    @EntityGraph(attributePaths = {"holder", "product", "coverages"})
    Optional<Policy> findWithCoveragesById(UUID id);

    @EntityGraph(attributePaths = {"coverages"})
    Optional<Policy> findWithCoveragesByPolicyNumber(String policyNumber);
}
