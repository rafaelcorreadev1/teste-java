package com.deroyque.activation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderResponseClassifierTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 202, 204})
    void successOn2xx(int status) {
        assertThat(ProviderResponseClassifier.classify(status, null, NOW)).isInstanceOf(ProviderResult.Success.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422})
    void permanentOnClientErrors(int status) {
        assertThat(ProviderResponseClassifier.classify(status, null, NOW))
                .isEqualTo(new ProviderResult.PermanentFailure(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void transientOnTimeoutThrottlingAndServerErrors(int status) {
        assertThat(ProviderResponseClassifier.classify(status, null, NOW))
                .isEqualTo(new ProviderResult.TransientFailure("PROVIDER_HTTP_" + status, Optional.empty()));
    }

    @Test
    void retryAfterInSeconds() {
        assertThat(ProviderResponseClassifier.classify(503, "30", NOW))
                .isEqualTo(new ProviderResult.TransientFailure("PROVIDER_HTTP_503", Optional.of(Duration.ofSeconds(30))));
    }

    @Test
    void retryAfterAsHttpDate() {
        assertThat(ProviderResponseClassifier.parseRetryAfter("Tue, 27 Oct 2026 12:01:30 GMT", NOW))
                .contains(Duration.ofSeconds(90));
    }

    @Test
    void invalidOrPastRetryAfter() {
        assertThat(ProviderResponseClassifier.parseRetryAfter("amanhã", NOW)).isEmpty();
        assertThat(ProviderResponseClassifier.parseRetryAfter("-5", NOW)).isEmpty();
        assertThat(ProviderResponseClassifier.parseRetryAfter("Tue, 27 Oct 2026 11:00:00 GMT", NOW)).contains(Duration.ZERO);
    }
}
