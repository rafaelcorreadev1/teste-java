package com.deroyque.activation.request.app;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Backoff exponencial com jitter: min(teto, base·2^(n−1)) × aleatório[0,5; 1,0).
 * O jitter evita que solicitações que falharam juntas voltem todas ao mesmo tempo.
 */
public class RetryPolicy {

    private final Duration base;
    private final Duration cap;
    private final int maxAttempts;
    private final RandomGenerator random;

    public RetryPolicy(Duration base, Duration cap, int maxAttempts, RandomGenerator random) {
        this.base = base;
        this.cap = cap;
        this.maxAttempts = maxAttempts;
        this.random = random;
    }

    public boolean hasAttemptsLeft(int attemptsMade) {
        return attemptsMade < maxAttempts;
    }

    public Instant nextAttemptAt(int attemptsMade, Optional<Duration> retryAfter, Instant now) {
        Duration backoff = jittered(exponential(attemptsMade));
        Duration delay = retryAfter.filter(requested -> requested.compareTo(backoff) > 0).orElse(backoff);
        return now.plus(min(delay, cap));
    }

    private Duration exponential(int attemptsMade) {
        int exponent = Math.max(0, Math.min(attemptsMade - 1, 30));
        return min(base.multipliedBy(1L << exponent), cap);
    }

    private Duration jittered(Duration delay) {
        double factor = 0.5 + random.nextDouble() * 0.5;
        return Duration.ofMillis(Math.round(delay.toMillis() * factor));
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
