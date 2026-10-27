package com.deroyque.activation.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

final class ProviderResponseClassifier {

    private ProviderResponseClassifier() {
    }

    static ProviderResult classify(int status, String retryAfterHeader, Instant now) {
        if (status >= 200 && status < 300) {
            return new ProviderResult.Success();
        }
        if (status == 408 || status == 429 || status >= 500) {
            return new ProviderResult.TransientFailure("PROVIDER_HTTP_" + status, parseRetryAfter(retryAfterHeader, now));
        }
        return new ProviderResult.PermanentFailure(status);
    }

    /** Retry-After pode vir em segundos ou como data HTTP (RFC 9110 §10.2.3). */
    static Optional<Duration> parseRetryAfter(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        try {
            long seconds = Long.parseLong(value);
            return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        } catch (NumberFormatException notSeconds) {
            try {
                Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration wait = Duration.between(now, at);
                return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
            } catch (DateTimeParseException invalid) {
                return Optional.empty();
            }
        }
    }
}
