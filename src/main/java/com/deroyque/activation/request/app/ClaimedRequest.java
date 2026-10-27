package com.deroyque.activation.request.app;

import java.util.UUID;

import com.deroyque.activation.request.domain.ActivationRequest;

record ClaimedRequest(UUID id, String customerId, String productCode, int attempt, String correlationId) {

    static ClaimedRequest from(ActivationRequest request) {
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : request.getId().toString();
        return new ClaimedRequest(request.getId(), request.getCustomerId(), request.getProductCode(),
                request.getAttempts(), correlationId);
    }
}
