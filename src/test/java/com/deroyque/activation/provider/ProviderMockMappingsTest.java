package com.deroyque.activation.provider;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class ProviderMockMappingsTest {

    @RegisterExtension
    static WireMockExtension composeMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory("docker/wiremock"))
            .build();

    private ProviderClient client;

    @BeforeEach
    void setUp() {
        ProviderProperties properties = new ProviderProperties(
                composeMock.baseUrl(), "k", Duration.ofSeconds(1), Duration.ofMillis(500));
        client = new ProviderClient(properties, RestClient.builder(), Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @ParameterizedTest
    @CsvSource({
            "INTERNET-500,   Success",
            "X-REJECT,       PermanentFailure",
            "X-ERROR,        TransientFailure",
            "X-SLOW,         TransientFailure",
    })
    void mappingProducesExpectedOutcome(String productCode, String expectedOutcome) {
        ProviderResult result = client.activate(
                new ProviderClient.ActivationCommand(UUID.randomUUID(), "CUST-1", productCode), "corr");

        assertThat(result.getClass().getSimpleName()).isEqualTo(expectedOutcome);
    }
}
