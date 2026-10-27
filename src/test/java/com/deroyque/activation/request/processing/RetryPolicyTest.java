package com.deroyque.activation.request.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final Duration BASE = Duration.ofSeconds(2);
    private static final Duration CAP = Duration.ofMinutes(5);

    private static RetryPolicy withRandom(double value) {
        RandomGenerator fixed = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
        return new RetryPolicy(BASE, CAP, 5, fixed);
    }

    private static Duration delay(RetryPolicy policy, int attempts, Optional<Duration> retryAfter) {
        return Duration.between(NOW, policy.nextAttemptAt(attempts, retryAfter, NOW));
    }

    @Test
    void growsExponentiallyWithoutJitter() {
        RetryPolicy noJitter = withRandom(0.999999);

        assertThat(delay(noJitter, 1, Optional.empty())).isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(5));
        assertThat(delay(noJitter, 2, Optional.empty())).isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(5));
        assertThat(delay(noJitter, 3, Optional.empty())).isCloseTo(Duration.ofSeconds(8), Duration.ofMillis(5));
        assertThat(delay(noJitter, 4, Optional.empty())).isCloseTo(Duration.ofSeconds(16), Duration.ofMillis(5));
    }

    @Test
    void neverExceedsCap() {
        assertThat(delay(withRandom(0.999999), 20, Optional.empty())).isLessThanOrEqualTo(CAP);
        assertThat(delay(withRandom(0.999999), 40, Optional.empty())).isLessThanOrEqualTo(CAP);
    }

    @Test
    void jitterHalvesDelayAtMinimum() {
        assertThat(delay(withRandom(0.0), 3, Optional.empty())).isEqualTo(Duration.ofSeconds(4));
    }

    @RepeatedTest(50)
    void realJitterStaysWithinRange() {
        RetryPolicy policy = new RetryPolicy(BASE, CAP, 5, RandomGenerator.getDefault());

        assertThat(delay(policy, 3, Optional.empty())).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(8));
    }

    @Test
    void honorsRetryAfterWhenLongerThanBackoff() {
        assertThat(delay(withRandom(0.0), 1, Optional.of(Duration.ofSeconds(30)))).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void retryAfterIsLimitedByCap() {
        assertThat(delay(withRandom(0.0), 1, Optional.of(Duration.ofHours(2)))).isEqualTo(CAP);
    }

    @Test
    void attemptsLeft() {
        RetryPolicy policy = withRandom(0.5);

        assertThat(policy.hasAttemptsLeft(4)).isTrue();
        assertThat(policy.hasAttemptsLeft(5)).isFalse();
    }
}
