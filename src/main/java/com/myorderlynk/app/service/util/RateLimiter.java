package com.myorderlynk.app.service.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory fixed-window rate limiter, keyed by an arbitrary string (e.g. client IP).
 * Suitable for a single instance; for a horizontally-scaled deployment back it with a shared store.
 * Stale windows are swept opportunistically once the tracked-key count grows large.
 */
@Component
public class RateLimiter {

    /** Above this many distinct keys, sweep expired windows on the next call to bound memory. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    /** Test seam: inject a controllable clock. */
    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Record a hit for {@code key} and report whether it is within the allowance.
     *
     * @return {@code true} if at most {@code maxRequests} have occurred in the current window,
     *         {@code false} once the limit is exceeded.
     */
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        long now = clock.millis();
        long windowMs = window.toMillis();
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start >= windowMs);
        }
        // Decide inside compute() so the count is read under the per-bin lock (no read-after races).
        int[] count = new int[1];
        windows.compute(key, (k, existing) -> {
            Window w = (existing == null || now - existing.start >= windowMs) ? new Window(now) : existing;
            w.count++;
            count[0] = w.count;
            return w;
        });
        return count[0] <= maxRequests;
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}