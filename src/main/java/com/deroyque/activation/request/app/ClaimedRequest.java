package com.deroyque.activation.request.app;

import java.util.UUID;

import com.deroyque.activation.request.domain.ActivationRequest;

record ClaimedRequest(UUID id, String customerId, String productCode, int attempt) {

    static ClaimedRequest from(ActivationRequest request) {
        return new ClaimedRequest(request.getId(), request.getCustomerId(), request.getProductCode(), request.getAttempts());
    }
}
