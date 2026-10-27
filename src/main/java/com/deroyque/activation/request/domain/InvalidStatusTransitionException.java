package com.deroyque.activation.request.domain;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ActivationStatus from, ActivationStatus to) {
        super("Transição de status inválida: %s -> %s".formatted(from, to));
    }
}
