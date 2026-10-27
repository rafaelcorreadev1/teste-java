package com.deroyque.activation.config;

import java.util.Random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.deroyque.activation.request.app.ActivationWorkerProperties;
import com.deroyque.activation.request.app.RetryPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ActivationWorkerProperties.class)
class ActivationWorkerConfig {

    @Bean
    RetryPolicy retryPolicy(ActivationWorkerProperties properties) {
        // RandomGenerator.getDefault() depende do módulo jdk.random, ausente na imagem JRE enxuta;
        // java.util.Random está no java.base e é thread-safe.
        return new RetryPolicy(properties.backoffBase(), properties.backoffCap(), properties.maxAttempts(),
                new Random());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
