package com.deroyque.activation.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("activation.provider")
public record ProviderProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout) {
}
