package com.deroyque.activation.request.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.deroyque.activation.request.app.NewActivationRequest;

public record CreateActivationRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String externalId,
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String customerId,
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER, message = IDENTIFIER_MESSAGE) String productCode) {

    static final String IDENTIFIER = "[A-Za-z0-9_-]*";
    static final String IDENTIFIER_MESSAGE = "deve conter apenas letras, números, '_' ou '-'";

    NewActivationRequest toCommand(String clientId) {
        return new NewActivationRequest(clientId, externalId, customerId, productCode);
    }
}
