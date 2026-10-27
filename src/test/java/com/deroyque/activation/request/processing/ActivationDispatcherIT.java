package com.deroyque.activation.request.processing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.deroyque.activation.request.app.ActivationRequestService;
import com.deroyque.activation.request.app.NewActivationRequest;
import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

@IntegrationTest
class ActivationDispatcherIT {

    private static final String ACTIVATE = "/provider/v1/activate";

    @Autowired
    ActivationDispatcher dispatcher;

    @Autowired
    ActivationRequestService service;

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    WireMockServer provider;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    Clock clock;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        provider.resetAll();
    }

    @Test
    void successCompletesRequest() {
        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(200)));
        ActivationRequest request = create("EXT-OK");

        assertThat(dispatcher.dispatchDueRequests()).isEqualTo(1);

        assertThat(reload(request)).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
            assertThat(r.getAttempts()).isEqualTo(1);
            assertThat(r.getFailureReason()).isNull();
        });
    }

    @Test
    void badRequestFailsWithoutRetry() {
        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(400)));
        ActivationRequest request = create("EXT-400");

        dispatcher.dispatchDueRequests();
        databaseCleaner.makeAllPendingDue();
        dispatcher.dispatchDueRequests();

        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(reload(request).getFailureReason()).isEqualTo("PROVIDER_REJECTED_400");
        provider.verify(1, postRequestedFor(urlEqualTo(ACTIVATE)));
    }

    @Test
    void serverErrorSchedulesRetryInTheFuture() {
        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(500)));
        ActivationRequest request = create("EXT-500");

        dispatcher.dispatchDueRequests();

        ActivationRequest reloaded = reload(request);
        assertThat(reloaded.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getNextAttemptAt()).isAfter(clock.instant());
        assertThat(dispatcher.dispatchDueRequests()).as("backoff ainda não venceu").isZero();
    }

    @Test
    void retryAfterIsRespected() {
        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(503).withHeader("Retry-After", "30")));
        ActivationRequest request = create("EXT-503");

        dispatcher.dispatchDueRequests();

        assertThat(reload(request).getNextAttemptAt()).isAfterOrEqualTo(clock.instant().plusSeconds(29));
    }

    @Test
    void exhaustingAttemptsFailsRequest() {
        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(500)));
        ActivationRequest request = create("EXT-RETRY");

        for (int attempt = 0; attempt < 5; attempt++) {
            databaseCleaner.makeAllPendingDue();
            dispatcher.dispatchDueRequests();
        }
        databaseCleaner.makeAllPendingDue();
        assertThat(dispatcher.dispatchDueRequests()).isZero();

        ActivationRequest reloaded = reload(request);
        assertThat(reloaded.getStatus()).isEqualTo(ActivationStatus.FAILED);
        assertThat(reloaded.getFailureReason()).isEqualTo("MAX_ATTEMPTS_EXCEEDED");
        assertThat(reloaded.getAttempts()).isEqualTo(5);
        provider.verify(5, postRequestedFor(urlEqualTo(ACTIVATE)));
    }

    @Test
    void retryAfterTimeoutReusesIdempotencyKey() {
        provider.stubFor(post(ACTIVATE).inScenario("lento").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(200).withFixedDelay(1_500))
                .willSetStateTo("normal"));
        provider.stubFor(post(ACTIVATE).inScenario("lento").whenScenarioStateIs("normal")
                .willReturn(aResponse().withStatus(200)));
        ActivationRequest request = create("EXT-TIMEOUT");

        dispatcher.dispatchDueRequests();
        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.PENDING);
        databaseCleaner.makeAllPendingDue();
        dispatcher.dispatchDueRequests();

        List<LoggedRequest> calls = provider.findAll(postRequestedFor(urlEqualTo(ACTIVATE)));
        assertThat(calls).hasSize(2)
                .extracting(call -> call.getHeader("Idempotency-Key"))
                .containsOnly(request.getId().toString());
        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void stuckProcessingIsReleasedAndResent() {
        ActivationRequest request = create("EXT-STUCK");
        simulateCrashDuringSend(request);

        assertThat(dispatcher.releaseStuckRequests()).isEqualTo(1);
        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.PENDING);

        provider.stubFor(post(ACTIVATE).willReturn(aResponse().withStatus(200)));
        dispatcher.dispatchDueRequests();

        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(reload(request).getAttempts()).isEqualTo(2);
    }

    @Test
    void recentProcessingIsNotReleased() {
        ActivationRequest request = create("EXT-RECENT");
        simulateSendInProgress(request);

        assertThat(dispatcher.releaseStuckRequests()).isZero();
        assertThat(reload(request).getStatus()).isEqualTo(ActivationStatus.PROCESSING);
    }

    private ActivationRequest create(String externalId) {
        return service.create(new NewActivationRequest("sys-a", externalId, "CUST-1", "INTERNET-500")).request();
    }

    private void simulateSendInProgress(ActivationRequest request) {
        ActivationRequest current = reload(request);
        current.startProcessing(clock.instant());
        repository.saveAndFlush(current);
    }

    private void simulateCrashDuringSend(ActivationRequest request) {
        simulateSendInProgress(request);
        databaseCleaner.ageProcessingStart();
    }

    private ActivationRequest reload(ActivationRequest request) {
        return repository.findById(request.getId()).orElseThrow();
    }
}
