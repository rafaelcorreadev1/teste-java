package com.deroyque.activation.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class ProviderClientTest {

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final UUID REQUEST_ID = UUID.fromString("0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90");
    private static final ProviderClient.ActivationCommand COMMAND =
            new ProviderClient.ActivationCommand(REQUEST_ID, "CUST-1", "INTERNET-500");

    private ProviderClient client;

    @BeforeEach
    void setUp() {
        ProviderProperties properties = new ProviderProperties(
                provider.baseUrl(), "secret-key", Duration.ofSeconds(1), Duration.ofMillis(500));
        client = new ProviderClient(properties, RestClient.builder(),
                Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void successSendsContractHeadersAndBody() {
        provider.stubFor(post(ProviderClient.ACTIVATE_PATH).willReturn(aResponse().withStatus(200)));

        assertThat(client.activate(COMMAND, "corr-1")).isInstanceOf(ProviderResult.Success.class);

        provider.verify(postRequestedFor(urlEqualTo(ProviderClient.ACTIVATE_PATH))
                .withHeader("Idempotency-Key", equalTo(REQUEST_ID.toString()))
                .withHeader("X-Correlation-Id", equalTo("corr-1"))
                .withHeader("X-Api-Key", equalTo("secret-key"))
                .withRequestBody(equalToJson("""
                        {"requestId":"0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90","customerId":"CUST-1","productCode":"INTERNET-500"}
                        """)));
    }

    @Test
    void badRequestIsPermanent() {
        provider.stubFor(post(ProviderClient.ACTIVATE_PATH).willReturn(aResponse().withStatus(400)));

        assertThat(client.activate(COMMAND, "corr-1")).isEqualTo(new ProviderResult.PermanentFailure(400));
    }

    @Test
    void serverErrorIsTransient() {
        provider.stubFor(post(ProviderClient.ACTIVATE_PATH).willReturn(aResponse().withStatus(500)));

        assertThat(client.activate(COMMAND, "corr-1"))
                .isEqualTo(new ProviderResult.TransientFailure("PROVIDER_HTTP_500", Optional.empty()));
    }

    @Test
    void serviceUnavailableCarriesRetryAfter() {
        provider.stubFor(post(ProviderClient.ACTIVATE_PATH)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));

        assertThat(client.activate(COMMAND, "corr-1"))
                .isEqualTo(new ProviderResult.TransientFailure("PROVIDER_HTTP_503", Optional.of(Duration.ofSeconds(30))));
    }

    @Test
    void responseAfterReadTimeoutIsTransient() {
        provider.stubFor(post(ProviderClient.ACTIVATE_PATH)
                .willReturn(aResponse().withStatus(200).withFixedDelay(1_500)));

        long start = System.nanoTime();
        ProviderResult result = client.activate(COMMAND, "corr-1");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).isEqualTo(ProviderResult.TransientFailure.of("PROVIDER_UNREACHABLE"));
        assertThat(elapsed).isLessThan(Duration.ofMillis(1_400));
    }

    @Test
    void connectionRefusedIsTransient() {
        ProviderProperties unreachable = new ProviderProperties(
                "http://localhost:1", "k", Duration.ofMillis(300), Duration.ofMillis(300));
        ProviderClient offline = new ProviderClient(unreachable, RestClient.builder(), Clock.systemUTC());

        assertThat(offline.activate(COMMAND, "corr-1")).isEqualTo(ProviderResult.TransientFailure.of("PROVIDER_UNREACHABLE"));
    }
}
