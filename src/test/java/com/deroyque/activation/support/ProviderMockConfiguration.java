package com.deroyque.activation.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Um WireMock simula as dependências HTTP externas: o provedor de ativação e o JWKS do emissor de tokens.
 * Bean em vez de {@code @DynamicPropertySource}: assim a URL não entra na chave do cache de contexto e
 * todas as classes de integração continuam compartilhando o mesmo contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ProviderMockConfiguration {

    public static final String JWKS_PATH = "/oauth/jwks";

    @Bean(destroyMethod = "stop")
    WireMockServer providerMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar externalServiceUrls(WireMockServer providerMock) {
        return registry -> {
            registry.add("activation.provider.base-url", providerMock::baseUrl);
            registry.add("activation.security.jwk-set-uri", () -> providerMock.baseUrl() + JWKS_PATH);
        };
    }
}
