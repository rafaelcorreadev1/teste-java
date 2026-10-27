package com.deroyque.activation.shared.security;

import java.util.stream.Stream;

import org.springframework.security.oauth2.jwt.Jwt;

/** Identifica o sistema consumidor dono das solicitações a partir do token. */
public final class ConsumerIdentity {

    private ConsumerIdentity() {
    }

    /** {@code azp} é o padrão OIDC para client credentials; alguns emissores usam {@code client_id}. */
    public static String of(Jwt jwt) {
        return Stream.of("azp", "client_id", "sub")
                .map(jwt::getClaimAsString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token sem identificação do cliente consumidor"));
    }
}
