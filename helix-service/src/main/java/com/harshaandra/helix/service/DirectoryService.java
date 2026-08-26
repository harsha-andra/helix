package com.harshaandra.helix.service;

import com.harshaandra.helix.domain.model.Adjuster;
import com.harshaandra.helix.domain.repository.ClaimantRepository;
import com.harshaandra.helix.domain.repository.AdjusterRepository;
import com.harshaandra.helix.service.dto.PartyDtos;
import com.harshaandra.helix.service.mapper.PartyMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DirectoryService {

    /**
     * Typeahead results are capped server-side. The UI debounces and cancels in-flight requests
     * (see the RxJS switchMap in helix-web), but a server must never rely on a client behaving.
     */
    private static final int TYPEAHEAD_LIMIT = 10;

    private final ClaimantRepository claimantRepository;
    private final AdjusterRepository adjusterRepository;
    private final PartyMapper partyMapper;

    public DirectoryService(ClaimantRepository claimantRepository,
                            AdjusterRepository adjusterRepository,
                            PartyMapper partyMapper) {
        this.claimantRepository = claimantRepository;
        this.adjusterRepository = adjusterRepository;
        this.partyMapper = partyMapper;
    }

    @Transactional(readOnly = true)
    public List<PartyDtos.Claimant> searchClaimants(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return partyMapper.toClaimants(
                claimantRepository.searchByPrefix(term.trim(), PageRequest.of(0, TYPEAHEAD_LIMIT)));
    }

    @Transactional(readOnly = true)
    public List<PartyDtos.Adjuster> listAdjusters() {
        return adjusterRepository.findByActiveTrueOrderByName().stream()
                .map(this::withWorkload)
                .toList();
    }

    private PartyDtos.Adjuster withWorkload(Adjuster adjuster) {
        return new PartyDtos.Adjuster(
                adjuster.getId(),
                adjuster.getName(),
                adjuster.getEmail(),
                adjusterRepository.countOpenClaims(adjuster.getId()));
    }
}
