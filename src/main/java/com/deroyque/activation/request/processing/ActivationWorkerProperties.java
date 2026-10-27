package com.deroyque.activation.request.processing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("activation.worker")
public record ActivationWorkerProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        Duration backoffBase,
        Duration backoffCap,
        Duration processingLease) {
}
