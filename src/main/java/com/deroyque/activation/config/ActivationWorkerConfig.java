package com.deroyque.activation.config;

import java.util.Random;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.deroyque.activation.request.processing.ActivationWorkerProperties;
import com.deroyque.activation.request.processing.RetryPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ActivationWorkerProperties.class)
class ActivationWorkerConfig {

    @Bean
    RetryPolicy retryPolicy(ActivationWorkerProperties properties) {
        return new RetryPolicy(properties.backoffBase(), properties.backoffCap(), properties.maxAttempts(),
                new Random());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
