package com.harshaandra.helix.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim lifecycle as a state machine. These run in milliseconds with no Spring context and
 * no database, which is why they can afford to be exhaustive.
 */
class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "SUBMITTED, UNDER_REVIEW",
            "SUBMITTED, DENIED",
            "SUBMITTED, CLOSED",
            "UNDER_REVIEW, APPROVED",
            "UNDER_REVIEW, PARTIALLY_APPROVED",
            "UNDER_REVIEW, DENIED",
            "APPROVED, PAID",
            "PARTIALLY_APPROVED, PAID",
            "PAID, CLOSED",
            "DENIED, UNDER_REVIEW"      // a denial can be reopened on appeal
    })
    void allowsLegalTransitions(ClaimStatus from, ClaimStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "SUBMITTED, APPROVED",       // must be reviewed first
            "SUBMITTED, PAID",
            "UNDER_REVIEW, PAID",        // must be approved before payment
            "APPROVED, DENIED",          // reverse an approval by closing, not by denying
            "PAID, APPROVED",
            "CLOSED, UNDER_REVIEW",      // closed is final
            "CLOSED, APPROVED"
    })
    void rejectsIllegalTransitions(ClaimStatus from, ClaimStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ClaimStatus.class)
    @DisplayName("no status can transition to itself")
    void selfTransitionIsNeverAllowed(ClaimStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    @DisplayName("CLOSED is a dead end")
    void closedIsTerminal() {
        for (ClaimStatus target : ClaimStatus.values()) {
            assertThat(ClaimStatus.CLOSED.canTransitionTo(target))
                    .as("CLOSED -> %s", target)
                    .isFalse();
        }
    }

    @Test
    void terminalStatusesAreTheOnesThatStopWork() {
        assertThat(ClaimStatus.PAID.isTerminal()).isTrue();
        assertThat(ClaimStatus.CLOSED.isTerminal()).isTrue();
        assertThat(ClaimStatus.DENIED.isTerminal()).isTrue();

        assertThat(ClaimStatus.SUBMITTED.isTerminal()).isFalse();
        assertThat(ClaimStatus.UNDER_REVIEW.isTerminal()).isFalse();
        assertThat(ClaimStatus.APPROVED.isTerminal()).isFalse();
        assertThat(ClaimStatus.PARTIALLY_APPROVED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("every status is reachable from SUBMITTED — no orphan states")
    void everyStatusIsReachable() {
        java.util.Set<ClaimStatus> reachable = new java.util.HashSet<>();
        java.util.Deque<ClaimStatus> frontier = new java.util.ArrayDeque<>();
        frontier.add(ClaimStatus.SUBMITTED);
        reachable.add(ClaimStatus.SUBMITTED);

        while (!frontier.isEmpty()) {
            ClaimStatus current = frontier.pop();
            for (ClaimStatus next : ClaimStatus.values()) {
                if (current.canTransitionTo(next) && reachable.add(next)) {
                    frontier.add(next);
                }
            }
        }

        assertThat(reachable).containsExactlyInAnyOrder(ClaimStatus.values());
    }
}
