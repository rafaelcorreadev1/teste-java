package com.deroyque.activation.shared.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.deroyque.activation.request.app.ActivationDispatcher;
import com.deroyque.activation.request.app.ActivationStatusMetrics;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;
import com.deroyque.activation.support.TestJwts;
import com.deroyque.activation.support.TestTokenIssuer;
import com.github.tomakehurst.wiremock.WireMockServer;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT {

    private static final String ACTIVATE = "/provider/v1/activate";

    @Autowired
    MockMvc mvc;

    @Autowired
    ActivationDispatcher dispatcher;

    @Autowired
    ActivationStatusMetrics statusMetrics;

    @Autowired
    WireMockServer externalServices;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        externalServices.resetAll();
    }

    @Test
    void correlationIdFromCreationReachesProvider() throws Exception {
        externalServices.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(200)));

        create("corr-abc-123");
        dispatcher.dispatchDueRequests();

        externalServices.verify(postRequestedFor(urlEqualTo(ACTIVATE))
                .withHeader("X-Correlation-Id", equalTo("corr-abc-123")));
    }

    @Test
    void workerLogsCarryCreationCorrelationId(CapturedOutput output) throws Exception {
        externalServices.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(500)));

        create("corr-worker-777");
        dispatcher.dispatchDueRequests();

        assertThat(output).containsPattern("\\[corr-worker-777\\].*ActivationDispatcher.*tentativa 1");
    }

    @Test
    void providerAndStatusMetricsAreExposedToPrometheus() throws Exception {
        externalServices.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(500)));
        create("corr-metrics");
        dispatcher.dispatchDueRequests();
        statusMetrics.refresh();

        String metrics = mvc.perform(get("/actuator/prometheus").with(TestJwts.fullAccess()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .containsPattern("activation_provider_call_seconds_count\\{[^}]*outcome=\"transient_failure\"")
                .containsPattern("activation_requests\\{[^}]*status=\"PENDING\"[^}]*} 1\\.0")
                .contains("hikaricp_connections_pending")
                .contains("http_server_requests_seconds");
    }

    @Test
    void prometheusRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void readinessIsUpWhenDatabaseIsReachable() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void bearerTokenIsNeverLogged(CapturedOutput output) throws Exception {
        TestTokenIssuer.publishJwks(externalServices);
        String bearer = TestTokenIssuer.token().bearer();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"x y\"}"))
                .andExpect(status().isBadRequest());

        assertThat(output).doesNotContain(bearer.substring("Bearer ".length()));
    }

    private void create(String correlationId) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/requests")
                        .with(TestJwts.fullAccess())
                        .header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"EXT-" + correlationId + "\",\"customerId\":\"C\",\"productCode\":\"P\"}"))
                .andExpect(status().isCreated());
    }
}
