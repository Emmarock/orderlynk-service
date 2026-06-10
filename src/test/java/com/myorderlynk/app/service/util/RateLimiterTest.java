package com.myorderlynk.app.service.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    /** A clock whose instant we can advance, so window behaviour is deterministic. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public long millis() {
            return now.toEpochMilli();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        Duration window = Duration.ofMinutes(1);

        assertThat(limiter.tryAcquire("ip-1", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, window)).as("4th hit exceeds the limit").isFalse();
        assertThat(limiter.tryAcquire("ip-1", 3, window)).isFalse();
    }

    @Test
    void resetsOnceTheWindowElapses() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(1);

        assertThat(limiter.tryAcquire("ip-1", 2, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 2, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 2, window)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("ip-1", 2, window)).as("new window after expiry").isTrue();
    }

    @Test
    void tracksEachKeyIndependently() {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        Duration window = Duration.ofMinutes(1);

        assertThat(limiter.tryAcquire("ip-1", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 1, window)).isFalse();
        // A different key has its own fresh allowance.
        assertThat(limiter.tryAcquire("ip-2", 1, window)).isTrue();
    }
}