package com.deroyque.activation.request.app;

import java.util.UUID;

public class ActivationRequestNotFoundException extends RuntimeException {

    public ActivationRequestNotFoundException(UUID id) {
        super("Solicitação %s não encontrada".formatted(id));
    }
}
