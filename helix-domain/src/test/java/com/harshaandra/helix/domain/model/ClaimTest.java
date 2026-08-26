package com.harshaandra.helix.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimTest {

    @Test
    @DisplayName("adding a line keeps both sides of the association consistent")
    void addLineSetsBothSides() {
        Claim claim = new Claim();
        ClaimLine line = lineOf("COLL", "1000.00");

        claim.addLine(line);

        assertThat(claim.getLines()).containsExactly(line);
        // Without this, the line's claim_id would be null on flush and the insert would fail
        // the not-null constraint — the classic "I set the parent but not the child" bug.
        assertThat(line.getClaim()).isSameAs(claim);
    }

    @Test
    void removingALineDetachesItAndRecalculates() {
        Claim claim = new Claim();
        ClaimLine keep = lineOf("COLL", "1000.00");
        ClaimLine drop = lineOf("COMP", "250.00");
        claim.addLine(keep);
        claim.addLine(drop);

        claim.removeLine(drop);

        assertThat(claim.getLines()).containsExactly(keep);
        assertThat(drop.getClaim()).isNull();
        assertThat(claim.getTotalAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("the total is derived from the lines, never set independently")
    void totalIsDerived() {
        Claim claim = new Claim();
        claim.addLine(lineOf("COLL", "1250.50"));
        claim.addLine(lineOf("COMP", "749.50"));

        assertThat(claim.getTotalAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void policyCoversDatesInsideItsTermOnly() {
        Policy policy = new Policy();
        policy.setEffectiveDate(LocalDate.of(2025, 1, 1));
        policy.setExpirationDate(LocalDate.of(2025, 12, 31));

        assertThat(policy.coversDate(LocalDate.of(2025, 6, 15))).isTrue();
        // Boundaries are inclusive — a loss on the first or last day of cover is covered.
        assertThat(policy.coversDate(LocalDate.of(2025, 1, 1))).isTrue();
        assertThat(policy.coversDate(LocalDate.of(2025, 12, 31))).isTrue();

        assertThat(policy.coversDate(LocalDate.of(2024, 12, 31))).isFalse();
        assertThat(policy.coversDate(LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void onlyActivePoliciesAcceptNewClaims() {
        assertThat(PolicyStatus.ACTIVE.allowsNewClaims()).isTrue();
        assertThat(PolicyStatus.LAPSED.allowsNewClaims()).isFalse();
        assertThat(PolicyStatus.CANCELLED.allowsNewClaims()).isFalse();
        assertThat(PolicyStatus.EXPIRED.allowsNewClaims()).isFalse();
        assertThat(PolicyStatus.QUOTED.allowsNewClaims()).isFalse();
    }

    @Test
    @DisplayName("an audit event exposes no setters — it is append-only by construction")
    void auditEventIsImmutable() {
        AuditEvent event = AuditEvent.of("Claim", java.util.UUID.randomUUID(),
                "CREATED", "tester", "detail");

        assertThat(event.getAction()).isEqualTo("CREATED");
        assertThat(event.getOccurredAt()).isNotNull();

        boolean hasSetter = java.util.Arrays.stream(AuditEvent.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("set"));
        assertThat(hasSetter)
                .as("AuditEvent must not expose setters — corrections are new rows, not edits")
                .isFalse();
    }

    private static ClaimLine lineOf(String coverageCode, String amount) {
        ClaimLine line = new ClaimLine();
        line.setCoverageCode(coverageCode);
        line.setClaimedAmount(new BigDecimal(amount));
        return line;
    }
}
