package com.deroyque.activation.config;

import java.util.random.RandomGenerator;

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
        return new RetryPolicy(properties.backoffBase(), properties.backoffCap(), properties.maxAttempts(),
                RandomGenerator.getDefault());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true")
    static class SchedulingConfig {
    }
}
