package com.harshaandra.helix.domain.model;

import java.util.Set;

public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    PARTIALLY_APPROVED,
    DENIED,
    PAID,
    CLOSED;

    /**
     * The claim lifecycle is a state machine, not a free-form field. Encoding the legal
     * transitions here keeps the rule in one place instead of scattered across controllers.
     */
    private static final Set<ClaimStatus> TERMINAL = Set.of(PAID, CLOSED, DENIED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(ClaimStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case SUBMITTED -> target == UNDER_REVIEW || target == DENIED || target == CLOSED;
            case UNDER_REVIEW -> target == APPROVED || target == PARTIALLY_APPROVED
                    || target == DENIED || target == CLOSED;
            case APPROVED, PARTIALLY_APPROVED -> target == PAID || target == CLOSED;
            case PAID -> target == CLOSED;
            case DENIED -> target == UNDER_REVIEW || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
