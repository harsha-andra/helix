package com.harshaandra.helix.service;

import com.harshaandra.helix.domain.model.Policy;
import com.harshaandra.helix.domain.repository.PolicyRepository;
import com.harshaandra.helix.service.dto.PolicyDtos;
import com.harshaandra.helix.service.exception.ServiceExceptions.NotFoundException;
import com.harshaandra.helix.service.mapper.PolicyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyMapper policyMapper;

    public PolicyService(PolicyRepository policyRepository, PolicyMapper policyMapper) {
        this.policyRepository = policyRepository;
        this.policyMapper = policyMapper;
    }

    @Transactional(readOnly = true)
    public Page<PolicyDtos.Summary> search(String term, Pageable pageable) {
        // Empty, never null - see the note on PolicyRepository#search.
        String normalised = (term == null || term.isBlank()) ? "" : term.trim();
        return policyRepository.search(normalised, pageable).map(policyMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PolicyDtos.Detail get(UUID id) {
        Policy policy = policyRepository.findWithCoveragesById(id)
                .orElseThrow(() -> new NotFoundException("Policy", id.toString()));
        return policyMapper.toDetail(policy);
    }

    @Transactional(readOnly = true)
    public PolicyDtos.Detail getByNumber(String policyNumber) {
        Policy policy = policyRepository.findWithCoveragesByPolicyNumber(policyNumber)
                .orElseThrow(() -> new NotFoundException("Policy", policyNumber));
        return policyMapper.toDetail(policy);
    }
}
