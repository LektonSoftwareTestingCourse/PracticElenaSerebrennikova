package com.processing.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {
    private final AtomicLong nanos = new AtomicLong();

    @Test
    void shouldRejectWhenBucketIsExhausted() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        assertThat(limiter.allowRequest("client-a")).isTrue();
        // Act
        boolean allowed = limiter.allowRequest("client-a");
        // Assert
        assertThat(allowed).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"999999999, false", "1000000000, true", "1000000001, true"})
    void shouldRefillWhenElapsedTimeReachesOneToken(long elapsedNanos, boolean expected) {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 1);
        assertThat(limiter.allowRequest("client-a")).isTrue();
        nanos.set(elapsedNanos);
        // Act
        boolean allowed = limiter.allowRequest("client-a");
        // Assert
        assertThat(allowed).isEqualTo(expected);
    }

    @Test
    void shouldKeepBucketsIndependentWhenClientsDiffer() {
        // Arrange
        InMemoryRateLimiter limiter = limiter(1, 0);
        assertThat(limiter.allowRequest("client-a")).isTrue();
        // Act
        boolean allowed = limiter.allowRequest("client-b");
        // Assert
        assertThat(allowed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void shouldRejectWhenCapacityIsNotPositive(int capacity) {
        // Arrange
        InMemoryRateLimiter limiter = limiter(capacity, 1);
        // Act
        boolean allowed = limiter.allowRequest("client-a");
        // Assert
        assertThat(allowed).isFalse();
    }

    private InMemoryRateLimiter limiter(int capacity, double refill) {
        return InMemoryRateLimiter.forTesting(capacity, refill, Duration.ofMinutes(10), 100, nanos::get);
    }
}
