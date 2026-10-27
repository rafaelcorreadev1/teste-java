package com.deroyque.activation.provider;

import java.time.Duration;
import java.util.Optional;

public sealed interface ProviderResult {

    record Success() implements ProviderResult {
    }

    record PermanentFailure(int httpStatus) implements ProviderResult {
    }

    record TransientFailure(String reason, Optional<Duration> retryAfter) implements ProviderResult {

        public static TransientFailure of(String reason) {
            return new TransientFailure(reason, Optional.empty());
        }
    }
}
