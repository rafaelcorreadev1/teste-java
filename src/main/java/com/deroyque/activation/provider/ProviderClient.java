package com.deroyque.activation.provider;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderClient {

    static final String ACTIVATE_PATH = "/provider/v1/activate";

    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    private final RestClient restClient;
    private final Clock clock;

    ProviderClient(ProviderProperties properties, RestClient.Builder builder, Clock clock) {
        // O padrão do JDK tenta upgrade h2c em HTTP puro, que vários servidores (inclusive o WireMock) abortam.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Api-Key", properties.apiKey())
                .build();
        this.clock = clock;
    }

    public ProviderResult activate(ActivationCommand command, String correlationId) {
        try {
            return restClient.post()
                    .uri(ACTIVATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", command.requestId().toString())
                    .header("X-Correlation-Id", correlationId)
                    .body(command)
                    .exchange((request, response) -> ProviderResponseClassifier.classify(
                            response.getStatusCode().value(),
                            response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                            clock.instant()));
        } catch (ResourceAccessException e) {
            log.warn("Falha de comunicação com o provedor para a solicitação {}: {}", command.requestId(), e.getMessage());
            return ProviderResult.TransientFailure.of("PROVIDER_UNREACHABLE");
        }
    }

    public record ActivationCommand(UUID requestId, String customerId, String productCode) {
    }
}
