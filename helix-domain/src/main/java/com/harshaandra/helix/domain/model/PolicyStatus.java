package com.harshaandra.helix.domain.model;

public enum PolicyStatus {
    QUOTED,
    ACTIVE,
    LAPSED,
    CANCELLED,
    EXPIRED;

    public boolean allowsNewClaims() {
        return this == ACTIVE;
    }
}
