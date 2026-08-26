package com.harshaandra.helix;

import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.domain.repository.PolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits every REST endpoint over HTTP and asserts it answers.
 *
 * WHY THIS EXISTS
 * ---------------
 * Two production-breaking bugs shipped past a suite of unit tests, an integration test for the
 * claims list, and a SOAP test, because nothing ever called the endpoint that was broken:
 *
 *   1. GET /api/v1/policies returned 500 on every request. The query used
 *      `(:term is null or lower(...) like ...)`, and with no search term PostgreSQL typed the
 *      untyped null as bytea and rejected `lower(bytea)`. The default path — no filter — was
 *      the broken one.
 *   2. GET /api/v1/claims/{id} threw MultipleBagFetchException because the entity graph
 *      join-fetched two bag collections.
 *
 * Both were invisible in code review and both are caught by the cheapest possible test: call the
 * endpoint and look at the status code. Sophisticated tests for some endpoints are not a
 * substitute for trivial tests across all of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "helix.seed.enabled=true")
@ActiveProfiles({"test", "local-noauth"})
@Tag("integration")
class ApiSmokeTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        TestDatabase.configure(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    @DisplayName("every collection endpoint answers 200 with no query parameters at all")
    void collectionEndpointsAnswerUnfiltered() {
        // Unfiltered is the default the UI issues on first load, and was the broken path.
        assertOk("/api/v1/claims");
        assertOk("/api/v1/policies");
        assertOk("/api/v1/adjusters");
        assertOk("/api/v1/dashboard/summary");
    }

    @Test
    @DisplayName("collection endpoints answer with paging and filters applied")
    void collectionEndpointsAnswerFiltered() {
        assertOk("/api/v1/claims?page=0&size=5");
        assertOk("/api/v1/claims?status=SUBMITTED");
        assertOk("/api/v1/claims?q=CLM");
        assertOk("/api/v1/claims?status=UNDER_REVIEW&q=CLM&page=0&size=10");
        assertOk("/api/v1/policies?page=0&size=5");
        assertOk("/api/v1/policies?q=POL");
        assertOk("/api/v1/claimants/search?q=Ma");
    }

    @Test
    @DisplayName("detail endpoints answer for a real id")
    void detailEndpointsAnswer() {
        UUID claimId = claimRepository.findAll().getFirst().getId();
        UUID policyId = policyRepository.findAll().getFirst().getId();

        assertOk("/api/v1/claims/" + claimId);
        assertOk("/api/v1/claims/" + claimId + "/audit");
        assertOk("/api/v1/policies/" + policyId);
    }

    @Test
    @DisplayName("a detail endpoint returns 404, not 500, for an id that does not exist")
    void unknownIdIsNotFound() {
        UUID missing = UUID.randomUUID();

        ResponseEntity<String> response = get("/api/v1/claims/" + missing);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("problems/not-found");
    }

    @Test
    @DisplayName("a malformed id is a client error, and leaks nothing")
    void malformedIdIsClientError() {
        ResponseEntity<String> response = get("/api/v1/claims/not-a-uuid");

        assertThat(response.getStatusCode().is4xxClientError())
                .as("a bad path variable is the caller's fault, not a server fault")
                .isTrue();
        assertThat(response.getBody()).doesNotContain("at com.harshaandra.helix");
    }

    @Test
    @DisplayName("the OpenAPI document and the WSDL are both served")
    void contractsAreServed() {
        assertOk("/v3/api-docs");
        assertOk("/ws/claims.wsdl");
    }

    @Test
    @DisplayName("health probes answer without authentication")
    void probesAnswer() {
        assertOk("/actuator/health");
        assertOk("/actuator/health/liveness");
        assertOk("/actuator/health/readiness");
    }

    private void assertOk(String path) {
        ResponseEntity<String> response = get(path);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("GET %s returned %s%n%s", path, response.getStatusCode(),
                        abbreviate(response.getBody()))
                .isTrue();
    }

    private ResponseEntity<String> get(String path) {
        return rest.getForEntity("http://localhost:" + port + path, String.class);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "(no body)";
        }
        return body.length() <= 400 ? body : body.substring(0, 400) + "...";
    }
}
