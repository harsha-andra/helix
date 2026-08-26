package com.harshaandra.helix;

import com.harshaandra.helix.domain.model.Claim;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.service.mapper.ClaimMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The N+1 regression test.
 *
 * This is the test behind the claim in docs/ARCHITECTURE.md. It does not eyeball a SQL log — it
 * asks Hibernate's own Statistics how many statements were prepared, so the numbers in the
 * documentation are measured rather than remembered, and a future change that reintroduces the
 * N+1 fails the build instead of quietly costing 200 queries a page.
 *
 * Both methods do exactly the same work from the caller's point of view: load a page of claims
 * and map it to DTOs. The only difference is how the query declares what it needs.
 */
@SpringBootTest(properties = {
        "helix.seed.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // Batch fetching is switched OFF here on purpose.
        //
        // HELIX's runtime config sets default_batch_fetch_size=50, which softens the N+1 into a
        // handful of batched selects. That is a good mitigation, but leaving it on for this test
        // would measure the mitigation instead of the defect, and would let someone delete the
        // @EntityGraph without the test noticing.
        //
        // -1 is Hibernate's own default. What this test measures is therefore what a team
        // actually hits on a stock configuration: lazy associations plus a mapper that reads
        // them. The fix being asserted is the entity graph, not the batch size.
        "spring.jpa.properties.hibernate.default_batch_fetch_size=-1"
})
@ActiveProfiles({"test", "local-noauth"})
@Tag("integration")
class ClaimFetchStrategyTest {

    private static final int PAGE_SIZE = 100;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        TestDatabase.configure(registry);
    }

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimMapper claimMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void resetStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @Transactional
    @DisplayName("the naive lazy fetch issues one query per claim per association")
    void naiveFetchExplodes() {
        Page<Claim> page = claimRepository.findAllNaive(PageRequest.of(0, PAGE_SIZE));

        // Force the same dereferences the DTO mapper performs.
        page.getContent().forEach(claim -> {
            claim.getPolicy().getPolicyNumber();
            claim.getClaimant().getFullName();
            claim.getLines().size();
            if (claim.getAdjuster() != null) {
                claim.getAdjuster().getName();
            }
        });

        long queries = statistics.getPrepareStatementCount();
        int loaded = page.getNumberOfElements();

        System.out.printf("NAIVE  : %d claims -> %d SQL statements%n", loaded, queries);

        // One query for the page, then further queries per row for the lazy associations.
        // Batch fetching keeps this below a strict 4N, but it is still linear in page size,
        // which is the actual defect.
        assertThat(queries)
                .as("the naive path scales with the number of rows")
                .isGreaterThan(loaded);
    }

    @Test
    @Transactional
    @DisplayName("the entity graph helps but is still linear — it does not cover the collection")
    void entityGraphIsOnlyHalfTheFix() {
        Page<Claim> page = claimRepository.findAllForListing(PageRequest.of(0, PAGE_SIZE));
        page.getContent().forEach(claimMapper::toSummary);

        long queries = statistics.getPrepareStatementCount();
        int loaded = page.getNumberOfElements();

        System.out.printf("GRAPH  : %d claims -> %d SQL statements%n", loaded, queries);

        // Materially better than naive: the three to-one associations are now one join.
        assertThat(queries)
                .as("the graph removes the to-one lookups")
                .isLessThan(loaded * 2L);

        // But still one select per row for `lines`, because the summary reads its size.
        // This assertion is deliberately pinned: if someone "fixes" it by turning batch
        // fetching back on, this test should fail and make them read the comment.
        assertThat(queries)
                .as("the graph does not cover the lines collection, so it is still linear")
                .isGreaterThan(loaded);
    }

    @Test
    @Transactional
    @DisplayName("the scalar projection is flat — 2 statements regardless of page size")
    void projectionIsFlat() {
        var page = claimRepository.findListRows(null, "", PageRequest.of(0, PAGE_SIZE));

        long queries = statistics.getPrepareStatementCount();
        int loaded = page.getNumberOfElements();

        System.out.printf("PROJ   : %d claims -> %d SQL statements%n", loaded, queries);

        assertThat(loaded).isGreaterThan(10);
        assertThat(queries)
                .as("one statement for the page, one for the count")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("naive -> projection is an order-of-magnitude reduction")
    void fixIsSubstantial() {
        Page<Claim> naive = claimRepository.findAllNaive(PageRequest.of(0, PAGE_SIZE));
        naive.getContent().forEach(claim -> {
            claim.getPolicy().getPolicyNumber();
            claim.getClaimant().getFullName();
            claim.getLines().size();
            if (claim.getAdjuster() != null) {
                claim.getAdjuster().getName();
            }
        });
        long naiveQueries = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();

        var projected = claimRepository.findListRows(null, "", PageRequest.of(0, PAGE_SIZE));
        assertThat(projected.getNumberOfElements()).isEqualTo(naive.getNumberOfElements());
        long projectedQueries = statistics.getPrepareStatementCount();

        System.out.printf("RESULT : %d -> %d SQL statements for %d claims (%.0fx fewer)%n",
                naiveQueries, projectedQueries, naive.getNumberOfElements(),
                projectedQueries == 0 ? 0 : (double) naiveQueries / projectedQueries);

        assertThat(projectedQueries).isLessThan(naiveQueries / 10);
    }
}
