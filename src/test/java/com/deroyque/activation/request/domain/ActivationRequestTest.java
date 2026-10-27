package com.deroyque.activation.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ActivationRequestTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);

    private final ActivationRequest request = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", T0);

    @Test
    void newRequestIsPendingWithEqualTimestamps() {
        assertThat(request.getId()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getCreatedAt()).isEqualTo(T0);
        assertThat(request.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void validTransitionUpdatesStatusAndUpdatedAt() {
        request.startProcessing(T1);
        request.complete(T2);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(request.getUpdatedAt()).isEqualTo(T2);
        assertThat(request.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void temporaryFailureReturnsToPending() {
        request.startProcessing(T1);
        request.returnToPending(T2);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
    }

    @Test
    void terminalStatusCannotChange() {
        request.startProcessing(T1);
        request.complete(T1);

        assertThatThrownBy(() -> request.startProcessing(T2))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("COMPLETED -> PROCESSING");
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(request.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void cannotSkipProcessing() {
        assertThatThrownBy(() -> request.complete(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> request.fail(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
    }

    @Test
    void comparesContent() {
        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-500")).isTrue();
        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-1000")).isFalse();
        assertThat(request.hasSameContentAs("CUST-2", "INTERNET-500")).isFalse();
    }
}
