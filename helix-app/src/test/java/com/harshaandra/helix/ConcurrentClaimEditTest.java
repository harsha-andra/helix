package com.harshaandra.helix;

import com.harshaandra.helix.domain.model.Claim;
import com.harshaandra.helix.domain.model.ClaimStatus;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.service.ClaimService;
import com.harshaandra.helix.service.command.ClaimCommands;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.exception.ServiceExceptions.IllegalStatusTransitionException;
import com.harshaandra.helix.service.exception.ServiceExceptions.StaleClaimException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
     * The service checks the version before touching anything, which produces the friendly 409.
     * This test bypasses that check and writes through the repository from two transactions, to
     * prove the @Version column is genuinely enforced by the database round trip and not merely
     * by our own if-statement.
     */
    @Test
    @DisplayName("@Version is enforced by the persistence layer, not only by the service check")
    void versionColumnIsEnforcedAtTheDatabase() {
        UUID claimId = anOpenClaim();

        Claim firstRead = transactionTemplate.execute(status ->
                claimRepository.findById(claimId).orElseThrow());
        Claim secondRead = transactionTemplate.execute(status ->
                claimRepository.findById(claimId).orElseThrow());

        // First transaction commits, bumping the version.
        transactionTemplate.execute(status -> {
            Claim managed = claimRepository.findById(claimId).orElseThrow();
            managed.setDescription("Updated by the first writer");
            return claimRepository.saveAndFlush(managed);
        });

        // Second transaction writes a detached instance carrying the now-stale version.
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            secondRead.setDescription("Updated by the second writer");
            return claimRepository.saveAndFlush(secondRead);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(firstRead.getVersion()).isEqualTo(secondRead.getVersion());
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
