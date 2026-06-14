package com.myorderlynk.app.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Builds a {@link Pageable} from raw {@code page}/{@code size} request params, clamping the size to a
 * sane range so a client can't ask for an unbounded page. Sorting stays encoded in the repository's
 * derived {@code ...OrderBy...} method names, so the Pageable only supplies offset + limit.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    /** Clamp a size value the same way {@link #of} does, for in-memory pagination. */
    public static int clampSize(int size) {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
