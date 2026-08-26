package com.harshaandra.helix;

import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.service.ClaimService;
import com.harshaandra.helix.service.dto.ClaimDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real SOAP endpoint over HTTP with a real SOAP envelope.
 *
 * Not a mock and not a unit test of the endpoint bean: this posts XML to the running
 * MessageDispatcherServlet, which routes by payload root, unmarshals with the JAXB classes
 * generated from claims.xsd, calls the same ClaimService the REST controller calls, and marshals
 * the response back. If any link in that chain breaks — a namespace typo in the XSD, a missing
 * JAXB binding, a servlet mapping change — this fails.
 *
 * The assertion that matters most is the last one: the SOAP response and the REST response
 * describe the same claim with the same numbers, because there is only one implementation
 * underneath. That is the entire justification for running both protocols.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "helix.seed.enabled=true")
@ActiveProfiles({"test", "local-noauth"})
@Tag("integration")
class SoapEndpointTest {

    private static final String NAMESPACE = "http://harsha-andra.dev/helix/claims";

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
    private ClaimService claimService;

    /**
     * Expected values come back as a DTO, not as an entity.
     *
     * Reading claim.getPolicy() off an entity returned by findAll() throws
     * LazyInitializationException: the test method is not a transaction, so the association has
     * no session to load through. Asking the service for a DTO gets fully-materialised values
     * and, usefully, compares the SOAP response against exactly what REST would return.
     */
    private ClaimDtos.Detail aSeededClaim() {
        return claimService.get(claimRepository.findAll().getFirst().getId());
    }

    @Test
    @DisplayName("GetClaimRequest returns the claim, its policy and its lines")
    void getClaimOverSoap() {
        ClaimDtos.Detail seeded = aSeededClaim();

        ResponseEntity<String> response = postSoap("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:cl="%s">
                  <soap:Body>
                    <cl:GetClaimRequest>
                      <cl:claimNumber>%s</cl:claimNumber>
                    </cl:GetClaimRequest>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(NAMESPACE, seeded.claimNumber()));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("GetClaimResponse");
        assertThat(body).contains("<ns2:claimNumber>" + seeded.claimNumber() + "</ns2:claimNumber>");
        assertThat(body).contains(seeded.policyNumber());
        assertThat(body).contains(seeded.claimantName());
        assertThat(body).contains("<ns2:status>" + seeded.status().name() + "</ns2:status>");

        // The XSD declares claim lines as repeating elements; a claim with lines must render them.
        if (!seeded.lines().isEmpty()) {
            assertThat(body).contains("<ns2:line>");
            assertThat(body).contains("<ns2:coverageCode>");
        }
    }

    @Test
    @DisplayName("ListClaimsRequest honours paging and reports the totals")
    void listClaimsOverSoap() {
        ResponseEntity<String> response = postSoap("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:cl="%s">
                  <soap:Body>
                    <cl:ListClaimsRequest>
                      <cl:page>0</cl:page>
                      <cl:size>5</cl:size>
                    </cl:ListClaimsRequest>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(NAMESPACE));

        String body = response.getBody();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(body).isNotNull();
        assertThat(body).contains("ListClaimsResponse");
        assertThat(body).contains("<ns2:totalElements>");

        long returned = body.split("<ns2:claim>", -1).length - 1;
        assertThat(returned)
                .as("the request asked for a page of 5")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("an unknown claim number produces a SOAP fault, not a 500 with a stack trace")
    void unknownClaimProducesFault() {
        ResponseEntity<String> response = postSoap("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:cl="%s">
                  <soap:Body>
                    <cl:GetClaimRequest>
                      <cl:claimNumber>CLM-0000-000000</cl:claimNumber>
                    </cl:GetClaimRequest>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(NAMESPACE));

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("Fault");
        // A fault string is fine; a Java stack trace on the wire is not.
        assertThat(body).doesNotContain("at com.harshaandra.helix");
        assertThat(body).doesNotContain("java.lang.");
    }

    @Test
    @DisplayName("the WSDL is published and describes the operations")
    void wsdlIsPublished() {
        ResponseEntity<String> wsdl = rest.getForEntity(
                "http://localhost:" + port + "/ws/claims.wsdl", String.class);

        assertThat(wsdl.getStatusCode().is2xxSuccessful()).isTrue();
        String body = wsdl.getBody();
        assertThat(body).isNotNull();

        assertThat(body).contains("wsdl:definitions");
        assertThat(body).contains("targetNamespace=\"" + NAMESPACE + "\"");
        assertThat(body).contains("ClaimsPort");
        // Generated from claims.xsd at runtime, so the operations must all be present.
        assertThat(body).contains("GetClaimRequest");
        assertThat(body).contains("ListClaimsRequest");
        assertThat(body).contains("GetPolicyRequest");
    }

    @Test
    @DisplayName("SOAP and REST report the same claim identically — one implementation underneath")
    void soapAndRestAgree() {
        ClaimDtos.Detail seeded = aSeededClaim();

        String soapBody = postSoap("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:cl="%s">
                  <soap:Body>
                    <cl:GetClaimRequest><cl:claimNumber>%s</cl:claimNumber></cl:GetClaimRequest>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(NAMESPACE, seeded.claimNumber())).getBody();

        String restBody = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/claims/" + seeded.id(), String.class).getBody();

        assertThat(soapBody).isNotNull();
        assertThat(restBody).isNotNull();

        assertThat(soapBody).contains(seeded.claimNumber());
        assertThat(restBody).contains(seeded.claimNumber());
        assertThat(soapBody).contains(seeded.status().name());
        assertThat(restBody).contains(seeded.status().name());
        assertThat(soapBody).contains(seeded.policyNumber());
        assertThat(restBody).contains(seeded.policyNumber());
    }

    private ResponseEntity<String> postSoap(String envelope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add("SOAPAction", "\"\"");
        return rest.postForEntity("http://localhost:" + port + "/ws",
                new HttpEntity<>(envelope, headers), String.class);
    }
}
