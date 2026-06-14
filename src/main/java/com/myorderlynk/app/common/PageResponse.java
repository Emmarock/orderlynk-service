package com.myorderlynk.app.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Uniform JSON envelope for every paginated list endpoint. Keeps the wire shape small and explicit
 * (Spring's own {@code Page} serialisation is verbose and unstable), so the frontend can rely on the
 * same fields everywhere: {@code content} plus the metadata needed to drive "load more".
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    /** Wrap a Spring Data {@link Page} (database-paginated results). */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /**
     * Paginate an already-materialised list in memory. Used for aggregated/computed results (e.g.
     * marketplace cards, customer rollups) that can't be paginated at the database level.
     */
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int safeSize = size <= 0 ? total : size;
        int from = Math.min(Math.max(page, 0) * safeSize, total);
        int to = safeSize == 0 ? total : Math.min(from + safeSize, total);
        int totalPages = safeSize == 0 ? 1 : (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(all.subList(from, to), page, safeSize, total, totalPages, to < total);
    }
}
