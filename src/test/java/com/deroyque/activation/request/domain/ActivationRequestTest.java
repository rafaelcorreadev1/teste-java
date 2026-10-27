package com.deroyque.activation.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ActivationRequestTest {

    private static final Instant T0 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);

    private final ActivationRequest request = ActivationRequest.create("sys-a", "EXT-1", "CUST-1", "INTERNET-500", T0);

    @Test
    void newRequestIsPendingAndDueImmediately() {
        assertThat(request.getId()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getCreatedAt()).isEqualTo(T0);
        assertThat(request.getUpdatedAt()).isEqualTo(T0);
        assertThat(request.getAttempts()).isZero();
        assertThat(request.getNextAttemptAt()).isEqualTo(T0);
        assertThat(request.getFailureReason()).isNull();
    }

    @Test
    void startProcessingCountsAttemptAndMarksStart() {
        request.startProcessing(T1);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PROCESSING);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getProcessingStartedAt()).isEqualTo(T1);
    }

    @Test
    void validTransitionUpdatesStatusAndUpdatedAt() {
        request.startProcessing(T1);
        request.complete(T2);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(request.getUpdatedAt()).isEqualTo(T2);
        assertThat(request.getCreatedAt()).isEqualTo(T0);
        assertThat(request.getProcessingStartedAt()).isNull();
    }

    @Test
    void temporaryFailureReturnsToPendingWithNextAttempt() {
        Instant nextAttempt = T2.plusSeconds(60);
        request.startProcessing(T1);
        request.returnToPending(nextAttempt, T2);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getNextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getProcessingStartedAt()).isNull();
    }

    @Test
    void failureRecordsReason() {
        request.startProcessing(T1);
        request.fail("PROVIDER_REJECTED_400", T2);

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(request.getFailureReason()).isEqualTo("PROVIDER_REJECTED_400");
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
        assertThat(request.getAttempts()).isEqualTo(1);
    }

    @Test
    void cannotSkipProcessing() {
        assertThatThrownBy(() -> request.complete(T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> request.fail("X", T1))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getFailureReason()).isNull();
    }

    @Test
    void comparesContent() {
        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-500")).isTrue();
        assertThat(request.hasSameContentAs("CUST-1", "INTERNET-1000")).isFalse();
        assertThat(request.hasSameContentAs("CUST-2", "INTERNET-500")).isFalse();
    }
}
