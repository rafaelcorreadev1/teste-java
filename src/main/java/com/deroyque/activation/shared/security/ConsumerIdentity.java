package com.deroyque.activation.shared.security;

import java.util.stream.Stream;

import org.springframework.security.oauth2.jwt.Jwt;

public final class ConsumerIdentity {

    private ConsumerIdentity() {
    }

    public static String of(Jwt jwt) {
        return Stream.of("azp", "client_id", "sub")
                .map(jwt::getClaimAsString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token sem identificação do cliente consumidor"));
    }
}
