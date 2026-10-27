package com.deroyque.activation.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} permitido? {2}")
    @CsvSource({
            "PENDING,    PENDING,    false",
            "PENDING,    PROCESSING, true",
            "PENDING,    COMPLETED,  false",
            "PENDING,    FAILED,     false",
            "PROCESSING, PENDING,    true",
            "PROCESSING, PROCESSING, false",
            "PROCESSING, COMPLETED,  true",
            "PROCESSING, FAILED,     true",
            "COMPLETED,  PENDING,    false",
            "COMPLETED,  PROCESSING, false",
            "COMPLETED,  COMPLETED,  false",
            "COMPLETED,  FAILED,     false",
            "FAILED,     PENDING,    false",
            "FAILED,     PROCESSING, false",
            "FAILED,     COMPLETED,  false",
            "FAILED,     FAILED,     false",
    })
    void transitionMatrix(ActivationStatus from, ActivationStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED", "FAILED"})
    void terminalStatesHaveNoTargets(ActivationStatus terminal) {
        assertThat(terminal.allowedTargets()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"PENDING", "PROCESSING"})
    void nonTerminalStatesHaveTargets(ActivationStatus status) {
        assertThat(status.allowedTargets()).isNotEmpty().doesNotContain(status);
    }
}
