package com.deroyque.activation.provider;

import java.time.Duration;
import java.util.Optional;

public sealed interface ProviderResult {

    record Success() implements ProviderResult {
    }

    /** O provedor recusou de forma definitiva; repetir daria o mesmo resultado. */
    record PermanentFailure(int httpStatus) implements ProviderResult {
    }

    /** Falha que pode passar sozinha. {@code retryAfter} vem do header Retry-After, quando o provedor o envia. */
    record TransientFailure(String reason, Optional<Duration> retryAfter) implements ProviderResult {

        public static TransientFailure of(String reason) {
            return new TransientFailure(reason, Optional.empty());
        }
    }
}
