package com.deroyque.activation.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        // timestamptz guarda microssegundos; com nanos, o POST e o GET devolveriam instantes diferentes.
        return Clock.tickMillis(ZoneOffset.UTC);
    }
}
