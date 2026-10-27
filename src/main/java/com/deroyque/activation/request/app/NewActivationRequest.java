package com.deroyque.activation.request.app;

public record NewActivationRequest(String clientId, String externalId, String customerId, String productCode) {
}
