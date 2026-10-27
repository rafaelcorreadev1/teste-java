package com.deroyque.activation.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("activation.security")
public record SecurityProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        Duration clockSkew) {
}
