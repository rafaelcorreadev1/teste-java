package com.deroyque.activation.request.app;

import com.deroyque.activation.request.domain.ActivationRequest;

public sealed interface CreateResult {

    ActivationRequest request();

    record Created(ActivationRequest request) implements CreateResult {
    }

    record Existing(ActivationRequest request) implements CreateResult {
    }
}
