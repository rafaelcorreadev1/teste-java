package com.deroyque.activation.request.app;

public record NewActivationRequest(String externalId, String customerId, String productCode) {
}
