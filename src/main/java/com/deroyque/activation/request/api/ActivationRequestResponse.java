package com.deroyque.activation.request.api;

import java.time.Instant;
import java.util.UUID;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;

public record ActivationRequestResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        ActivationStatus status,
        int attempts,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    static ActivationRequestResponse from(ActivationRequest request) {
        return new ActivationRequestResponse(
                request.getId(),
                request.getExternalId(),
                request.getCustomerId(),
                request.getProductCode(),
                request.getStatus(),
                request.getAttempts(),
                request.getFailureReason(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
