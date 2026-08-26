package com.harshaandra.helix.api.soap;

import com.harshaandra.helix.api.soap.generated.*;
import com.harshaandra.helix.service.ClaimService;
import com.harshaandra.helix.service.PolicyService;
import com.harshaandra.helix.service.dto.ClaimDtos;
import com.harshaandra.helix.service.dto.PolicyDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * SOAP façade over the claims domain.
 *
 * The point of this class is what it does NOT contain: no business rules, no repository access,
 * no validation logic of its own. It translates XML into a service call and the result back into
 * XML. {@link ClaimService} is the identical bean {@code ClaimController} calls.
 *
 * That is the whole argument for keeping SOAP alive next to REST here. Partner systems that
 * cannot move off SOAP and the Angular client that speaks REST cannot diverge, because there is
 * only one implementation of "what happens when a claim's status changes". A second
 * implementation behind the legacy protocol is how two channels start giving different answers
 * to the same question — and the SOAP one is always the one nobody notices is wrong.
 */
@Endpoint
public class ClaimsEndpoint {

    private static final String NAMESPACE = "http://harsha-andra.dev/helix/claims";

    private final ClaimService claimService;
    private final PolicyService policyService;

    public ClaimsEndpoint(ClaimService claimService, PolicyService policyService) {
        this.claimService = claimService;
        this.policyService = policyService;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "GetClaimRequest")
    @ResponsePayload
    public GetClaimResponse getClaim(@RequestPayload GetClaimRequest request) {
        ClaimDtos.Detail detail = claimService.getByNumber(request.getClaimNumber());

        Claim claim = new Claim();
        claim.setClaimNumber(detail.claimNumber());
        claim.setPolicyNumber(detail.policyNumber());
        claim.setClaimantName(detail.claimantName());
        claim.setStatus(ClaimStatus.fromValue(detail.status().name()));
        claim.setIncidentDate(XmlTypes.toXmlDate(detail.incidentDate()));
        claim.setSubmittedAt(XmlTypes.toXmlDateTime(detail.submittedAt()));
        claim.setTotalAmount(detail.totalAmount());
        claim.setApprovedAmount(detail.approvedAmount());
        claim.setAssignedAdjuster(detail.assignedAdjuster());
        claim.setDescription(detail.description());
        claim.setVersion(detail.version());

        detail.lines().forEach(line -> {
            ClaimLine xml = new ClaimLine();
            xml.setLineNumber(line.lineNumber());
            xml.setCoverageCode(line.coverageCode());
            xml.setDescription(line.description());
            xml.setClaimedAmount(line.claimedAmount());
            xml.setApprovedAmount(line.approvedAmount());
            xml.setStatus(line.status());
            claim.getLine().add(xml);
        });

        GetClaimResponse response = new GetClaimResponse();
        response.setClaim(claim);
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "ListClaimsRequest")
    @ResponsePayload
    public ListClaimsResponse listClaims(@RequestPayload ListClaimsRequest request) {
        int page = request.getPage() == null ? 0 : Math.max(0, request.getPage());
        int size = request.getSize() == null ? 25 : Math.clamp(request.getSize(), 1, 100);

        com.harshaandra.helix.domain.model.ClaimStatus status = request.getStatus() == null
                ? null
                : com.harshaandra.helix.domain.model.ClaimStatus.valueOf(request.getStatus().value());

        var result = claimService.list(status, null,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")));

        ListClaimsResponse response = new ListClaimsResponse();
        result.getContent().forEach(summary -> {
            ClaimSummary xml = new ClaimSummary();
            xml.setClaimNumber(summary.claimNumber());
            xml.setPolicyNumber(summary.policyNumber());
            xml.setClaimantName(summary.claimantName());
            xml.setStatus(ClaimStatus.fromValue(summary.status().name()));
            xml.setIncidentDate(XmlTypes.toXmlDate(summary.incidentDate()));
            xml.setTotalAmount(summary.totalAmount());
            response.getClaim().add(xml);
        });
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setPage(result.getNumber());
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "GetPolicyRequest")
    @ResponsePayload
    public GetPolicyResponse getPolicy(@RequestPayload GetPolicyRequest request) {
        PolicyDtos.Detail detail = policyService.getByNumber(request.getPolicyNumber());

        GetPolicyResponse response = new GetPolicyResponse();
        response.setPolicyNumber(detail.policyNumber());
        response.setProductName(detail.productName());
        response.setHolderName(detail.holderName());
        response.setStatus(detail.status().name());
        response.setEffectiveDate(XmlTypes.toXmlDate(detail.effectiveDate()));
        response.setExpirationDate(XmlTypes.toXmlDate(detail.expirationDate()));
        response.setPremiumAmount(detail.premiumAmount());

        detail.coverages().forEach(coverage -> {
            GetPolicyResponse.Coverage xml = new GetPolicyResponse.Coverage();
            xml.setCode(coverage.code());
            xml.setName(coverage.name());
            xml.setLimitAmount(coverage.limitAmount());
            xml.setDeductibleAmount(coverage.deductibleAmount());
            response.getCoverage().add(xml);
        });
        return response;
    }
}
