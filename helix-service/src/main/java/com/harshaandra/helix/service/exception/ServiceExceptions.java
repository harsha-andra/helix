package com.harshaandra.helix.service.exception;

import com.harshaandra.helix.domain.model.ClaimStatus;

/**
 * Domain-meaningful failures. These carry enough information for the web layer to turn them
 * into an RFC 7807 problem document without the web layer having to know any business rules.
 */
public final class ServiceExceptions {

    private ServiceExceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        private final String resourceType;
        private final String identifier;

        public NotFoundException(String resourceType, String identifier) {
            super(resourceType + " '" + identifier + "' was not found");
            this.resourceType = resourceType;
            this.identifier = identifier;
        }

        public String getResourceType() {
            return resourceType;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    /** An otherwise valid request that the current state of the claim does not permit. */
    public static class IllegalStatusTransitionException extends RuntimeException {
        private final ClaimStatus from;
        private final ClaimStatus to;

        public IllegalStatusTransitionException(ClaimStatus from, ClaimStatus to) {
            super("A claim in status " + from + " cannot move to " + to);
            this.from = from;
            this.to = to;
        }

        public ClaimStatus getFrom() {
            return from;
        }

        public ClaimStatus getTo() {
            return to;
        }
    }

    /** Someone else saved this claim after we read it. Maps to HTTP 409. */
    public static class StaleClaimException extends RuntimeException {
        private final int expectedVersion;
        private final int actualVersion;

        public StaleClaimException(int expectedVersion, int actualVersion) {
            super("This claim was modified by someone else (you had version " + expectedVersion
                    + ", the current version is " + actualVersion + "). Reload and try again.");
            this.expectedVersion = expectedVersion;
            this.actualVersion = actualVersion;
        }

        public int getExpectedVersion() {
            return expectedVersion;
        }

        public int getActualVersion() {
            return actualVersion;
        }
    }

    /** A rule violation that is not a field-level validation failure. Maps to HTTP 422. */
    public static class BusinessRuleException extends RuntimeException {
        private final String rule;

        public BusinessRuleException(String rule, String message) {
            super(message);
            this.rule = rule;
        }

        public String getRule() {
            return rule;
        }
    }
}
