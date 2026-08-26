package com.harshaandra.helix.domain.repository;

import com.harshaandra.helix.domain.model.Claim;
import com.harshaandra.helix.domain.model.ClaimStatus;
import com.harshaandra.helix.domain.projection.ClaimListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    /**
     * ---------------------------------------------------------------------------------------
     * THE N+1 PATH — kept deliberately, and exercised by ClaimFetchStrategyTest.
     * ---------------------------------------------------------------------------------------
     * Every association on Claim is LAZY. This method emits one query for the page of claims,
     * and then the DTO mapper dereferences policy, claimant, adjuster and lines on each row,
     * emitting four more queries per claim. At a page size of 50 that is 1 + (4 x 50) = 201
     * queries to render one list.
     *
     * This is not a hypothetical. It is the default outcome of pairing lazy associations with
     * a mapper that reads them, and it is invisible in code review because the mapper looks
     * innocent. It only shows up in the SQL log or in a query-count assertion.
     *
     * Use {@link #findAllForListing(Pageable)} instead. This one exists so the regression test
     * can prove the difference, and so ARCHITECTURE.md can cite real numbers.
     */
    @Query("select c from Claim c")
    Page<Claim> findAllNaive(Pageable pageable);

    /**
     * THE PARTIAL FIX — the first thing that gets tried, and the reason this repository has a
     * third list method below it.
     *
     * The entity graph collapses the three to-one associations (policy, claimant, adjuster) into
     * a single join, which removes most of the queries the naive path emits. Measured on a page
     * of 50: 103 statements down to 52.
     *
     * 52, not 2. The remaining 50 are the `lines` collection: the summary carries a line count,
     * `getLines().size()` initialises the collection, and that is one select per row. The graph
     * fixed the associations it named and left the one it did not.
     *
     * That gap is the point. "Add an @EntityGraph" is the standard answer to N+1 and it is
     * only half an answer here — which is invisible unless the queries are actually counted.
     * {@link #findListRows} is what finished the job.
     */
    @EntityGraph(attributePaths = {"policy", "claimant", "adjuster"})
    @Query("select c from Claim c")
    Page<Claim> findAllForListing(Pageable pageable);

    @EntityGraph(attributePaths = {"policy", "claimant", "adjuster"})
    @Query("""
            select c from Claim c
            where (:status is null or c.status = :status)
              and (:term is null or lower(c.claimNumber) like lower(concat('%', :term, '%'))
                   or lower(c.claimant.lastName) like lower(concat('%', :term, '%'))
                   or lower(c.policy.policyNumber) like lower(concat('%', :term, '%')))
            """)
    Page<Claim> search(@Param("status") ClaimStatus status,
                       @Param("term") String term,
                       Pageable pageable);

    /**
     * THE ACTUAL LIST QUERY. One statement for the page, one for the count.
     *
     * The entity-graph version above was a real improvement — it collapsed the three to-one
     * associations into a single join — but it was still not flat, because the summary needs a
     * line count and {@code claim.getLines().size()} initialises the collection: one more select
     * per row. Measuring is what caught that; the graph looked like the finish line and was not.
     *
     * Adding "lines" to the entity graph is not the answer either. Join-fetching a collection
     * alongside pagination produces a cartesian product, and Hibernate can only honour the page
     * boundaries by pulling the whole result set into memory and slicing it there. That is why
     * hibernate.query.fail_on_pagination_over_collection_fetch is set to true in application.yml:
     * it turns that silent memory blow-up into a startup-time error.
     *
     * Selecting scalars is what actually makes it flat. `size(c.lines)` becomes a correlated
     * subquery the database evaluates per row, at a cost the planner understands and an index
     * on claim_line.claim_id serves.
     *
     * `term` is an empty string rather than null when no search is active. That is not a style
     * preference: PostgreSQL infers the type of an untyped JDBC null as bytea, so a null here
     * makes the driver ask for lower(bytea) and the statement fails outright. An empty term
     * turns the predicate into LIKE '%%', which matches every row and keeps one query plan for
     * both the filtered and unfiltered cases.
     */
    @Query(value = """
            select new com.harshaandra.helix.domain.projection.ClaimListRow(
                c.id, c.claimNumber, p.policyNumber, cl.firstName, cl.lastName,
                c.status, c.totalAmount, c.incidentDate, c.submittedAt,
                a.name, size(c.lines), c.version)
            from Claim c
            join c.policy p
            join c.claimant cl
            left join c.adjuster a
            where (:status is null or c.status = :status)
              and (lower(c.claimNumber) like lower(concat('%', :term, '%'))
                   or lower(cl.lastName) like lower(concat('%', :term, '%'))
                   or lower(p.policyNumber) like lower(concat('%', :term, '%')))
            """,
            countQuery = """
            select count(c) from Claim c
            join c.policy p
            join c.claimant cl
            where (:status is null or c.status = :status)
              and (lower(c.claimNumber) like lower(concat('%', :term, '%'))
                   or lower(cl.lastName) like lower(concat('%', :term, '%'))
                   or lower(p.policyNumber) like lower(concat('%', :term, '%')))
            """)
    Page<ClaimListRow> findListRows(@Param("status") ClaimStatus status,
                                    @Param("term") String term,
                                    Pageable pageable);

    /** Detail view: everything needed for one claim, without a second round trip per section. */
    @EntityGraph(attributePaths = {"policy", "policy.coverages", "claimant", "adjuster", "lines", "documents"})
    Optional<Claim> findWithDetailById(UUID id);

    long countByStatus(ClaimStatus status);

    @Query("select c.status as status, count(c) as total from Claim c group by c.status")
    List<StatusCount> countGroupedByStatus();

    @Query("""
            select coalesce(sum(c.totalAmount), 0) from Claim c
            where c.status not in (com.harshaandra.helix.domain.model.ClaimStatus.CLOSED,
                                   com.harshaandra.helix.domain.model.ClaimStatus.DENIED)
            """)
    java.math.BigDecimal sumOpenReserves();

    @Query("select c from Claim c where c.submittedAt >= :since")
    List<Claim> findSubmittedSince(@Param("since") Instant since);

    /** Projection so the dashboard aggregate does not have to load entities. */
    interface StatusCount {
        ClaimStatus getStatus();
        long getTotal();
    }
}
