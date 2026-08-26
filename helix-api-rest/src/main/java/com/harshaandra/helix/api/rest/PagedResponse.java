package com.harshaandra.helix.api.rest;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Explicit page envelope rather than serialising Spring's PageImpl directly.
 *
 * PageImpl's JSON shape is not part of Spring Data's API contract — Boot 3.3 warns about
 * exactly this — so serialising it straight out means a Spring upgrade can silently reshape a
 * public API. Declaring the envelope here makes the contract ours, and it is the shape the
 * Angular client is written against.
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
