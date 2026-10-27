package com.deroyque.activation.request.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.deroyque.activation.provider.ProviderResult;
import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.request.infra.ActivationRequestRepository;

/**
 * Transações curtas do processamento. Fica separado do {@link ActivationDispatcher} porque
 * {@code @Transactional} é aplicado por proxy e seria ignorado em chamadas internas.
 */
@Component
class ActivationStateStore {

    static final String MAX_ATTEMPTS_EXCEEDED = "MAX_ATTEMPTS_EXCEEDED";

    private static final Logger log = LoggerFactory.getLogger(ActivationStateStore.class);

    private final ActivationRequestRepository repository;
    private final RetryPolicy retryPolicy;
    private final ActivationWorkerProperties properties;

    ActivationStateStore(ActivationRequestRepository repository, RetryPolicy retryPolicy,
            ActivationWorkerProperties properties) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
    }

    @Transactional
    public List<ClaimedRequest> claimBatch(Instant now) {
        List<ActivationRequest> due = repository.lockDuePending(now, properties.batchSize());
        due.forEach(request -> request.startProcessing(now));
        return due.stream().map(ClaimedRequest::from).toList();
    }

    @Transactional
    public void applyResult(UUID id, ProviderResult result, Instant now) {
        ActivationRequest request = repository.findById(id).orElseThrow();
        if (request.getStatus() != ActivationStatus.PROCESSING) {
            log.warn("Resultado do provedor ignorado: a solicitação {} já está em {}", id, request.getStatus());
            return;
        }
        switch (result) {
            case ProviderResult.Success success -> request.complete(now);
            case ProviderResult.PermanentFailure failure ->
                    request.fail("PROVIDER_REJECTED_" + failure.httpStatus(), now);
            case ProviderResult.TransientFailure failure when retryPolicy.hasAttemptsLeft(request.getAttempts()) ->
                    request.returnToPending(
                            retryPolicy.nextAttemptAt(request.getAttempts(), failure.retryAfter(), now), now);
            case ProviderResult.TransientFailure failure -> request.fail(MAX_ATTEMPTS_EXCEEDED, now);
        }
    }

    @Transactional
    public boolean releaseStuck(UUID id, Instant threshold, Instant now) {
        ActivationRequest request = repository.findById(id).orElseThrow();
        if (request.getStatus() != ActivationStatus.PROCESSING || !request.getProcessingStartedAt().isBefore(threshold)) {
            return false;
        }
        if (retryPolicy.hasAttemptsLeft(request.getAttempts())) {
            request.returnToPending(now, now);
        } else {
            request.fail(MAX_ATTEMPTS_EXCEEDED, now);
        }
        return true;
    }
}
