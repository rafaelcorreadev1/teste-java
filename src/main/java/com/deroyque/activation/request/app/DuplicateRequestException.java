package com.deroyque.activation.request.app;

public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String externalId) {
        super("Já existe uma solicitação com externalId %s e conteúdo diferente".formatted(externalId));
    }
}
