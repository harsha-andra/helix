package com.harshaandra.helix;

import com.harshaandra.helix.domain.model.Claim;
import com.harshaandra.helix.domain.model.ClaimStatus;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.service.ClaimService;
import com.harshaandra.helix.service.command.ClaimCommands;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.exception.ServiceExceptions.IllegalStatusTransitionException;
import com.harshaandra.helix.service.exception.ServiceExceptions.StaleClaimException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two adjusters open the same claim and both save.
 *
 * This is the scenario @Version exists for. Without it the second write silently overwrites the
 * first and nobody finds out until a customer asks why their claim was denied when an adjuster
 * remembers approving it. The test proves the second write is rejected — at the service layer
 * with a useful error, and at the database layer as a genuine lost-update guard.
 */
@SpringBootTest(properties = "helix.seed.enabled=true")
@ActiveProfiles({"test", "local-noauth"})
@Tag("integration")
class ConcurrentClaimEditTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        TestDatabase.configure(registry);
    }

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Commits on its own connection, so it survives our rollback. */
    private TransactionTemplate competingWriter;

    @BeforeEach
    void prepareCompetingWriter() {
        competingWriter = new TransactionTemplate(transactionTemplate.getTransactionManager());
        competingWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("the second adjuster's save is rejected, not silently applied")
    void secondWriterIsRejected() {
        UUID claimId = anOpenClaim();

        // Both adjusters load the claim. Both see the same version.
        ClaimDtos.Detail asSeenByDana = claimService.get(claimId);
        ClaimDtos.Detail asSeenByMarcus = claimService.get(claimId);
        assertThat(asSeenByDana.version()).isEqualTo(asSeenByMarcus.version());

        // Dana saves first and wins.
        ClaimDtos.Detail afterDana = claimService.changeStatus(claimId,
                new ClaimCommands.ChangeStatus(ClaimStatus.UNDER_REVIEW, asSeenByDana.version(),
                        "Picking this up"),
                "dana.whitfield");

        assertThat(afterDana.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(afterDana.version()).isGreaterThan(asSeenByDana.version());

        // Marcus saves second, still holding the stale version he read.
        assertThatThrownBy(() -> claimService.changeStatus(claimId,
                new ClaimCommands.ChangeStatus(ClaimStatus.DENIED, asSeenByMarcus.version(),
                        "Denying this"),
                "marcus.bell"))
                .isInstanceOf(StaleClaimException.class)
                .satisfies(thrown -> {
                    StaleClaimException stale = (StaleClaimException) thrown;
                    assertThat(stale.getExpectedVersion()).isEqualTo(asSeenByMarcus.version());
                    assertThat(stale.getActualVersion()).isEqualTo(afterDana.version());
                });

        // And the claim still holds Dana's decision, not Marcus's.
        assertThat(claimService.get(claimId).status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    /**
     * The service pre-checks the version, which is what produces the friendly 409. This test
     * proves the guarantee underneath it: that the UPDATE itself carries the version in its
     * WHERE clause, so a lost update is impossible even if the pre-check were removed.
     *
     * The competing write is issued with plain JDBC, behind Hibernate's back. That is what makes
     * this deterministic — it is exactly what another application instance committing between
     * our read and our write looks like from this session's point of view, without needing two
     * threads and a latch to reproduce it.
     */
    @Test
    @DisplayName("@Version is enforced by the UPDATE itself, not only by the service check")
    void versionColumnIsEnforcedAtTheDatabase() {
        UUID claimId = anOpenClaim();

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            Claim managed = claimRepository.findById(claimId).orElseThrow();
            int versionWeRead = managed.getVersion();

            // Another node commits a change to this row while we hold our copy.
            //
            // REQUIRES_NEW is what makes this a genuine simulation: it takes its own connection
            // and commits independently. Issuing the update on the current transaction's
            // connection would enlist it in our transaction, and it would roll back with us -
            // which is precisely the mistake that made the first version of this test pass its
            // exception assertion and then fail on the state assertion below.
            int rowsUpdated = competingWriter.execute(other -> jdbcTemplate.update(
                    "UPDATE claim SET version = version + 1, description = ? WHERE id = ?",
                    "Committed by another instance", claimId));
            assertThat(rowsUpdated).isEqualTo(1);

            // Our write now targets a version that no longer exists:
            //   UPDATE claim SET ... WHERE id = ? AND version = <versionWeRead>
            // matches zero rows, and Hibernate turns "zero rows updated" into a lock failure
            // rather than silently doing nothing.
            managed.setDescription("Committed by us, based on a stale read");
            assertThat(versionWeRead).isNotNegative();
            return claimRepository.saveAndFlush(managed);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The other instance's write is the one that survived.
        assertThat(claimService.get(claimId).description())
                .isEqualTo("Committed by another instance");
    }

    @Test
    @DisplayName("an illegal transition is rejected even with a current version")
    void illegalTransitionIsRejected() {
        UUID claimId = anOpenClaim();
        ClaimDtos.Detail current = claimService.get(claimId);

        // SUBMITTED cannot jump straight to PAID.
        assertThatThrownBy(() -> claimService.changeStatus(claimId,
                new ClaimCommands.ChangeStatus(ClaimStatus.PAID, current.version(), null),
                "dana.whitfield"))
                .isInstanceOf(IllegalStatusTransitionException.class);
    }

    private UUID anOpenClaim() {
        return claimRepository.findAll().stream()
                .filter(claim -> claim.getStatus() == ClaimStatus.SUBMITTED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("seed data has no SUBMITTED claim"))
                .getId();
    }
}
