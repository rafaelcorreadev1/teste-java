package com.deroyque.activation.request.processing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.deroyque.activation.provider.ProviderClient;
import com.deroyque.activation.provider.ProviderResult;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.shared.web.CorrelationIdFilter;

@Component
public class ActivationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ActivationDispatcher.class);

    private final ActivationStateStore stateStore;
    private final ActivationRequestRepository repository;
    private final ProviderClient providerClient;
    private final ActivationWorkerProperties properties;
    private final Clock clock;

    ActivationDispatcher(ActivationStateStore stateStore, ActivationRequestRepository repository,
            ProviderClient providerClient, ActivationWorkerProperties properties, Clock clock) {
        this.stateStore = stateStore;
        this.repository = repository;
        this.providerClient = providerClient;
        this.properties = properties;
        this.clock = clock;
    }

    public int dispatchDueRequests() {
        List<ClaimedRequest> claimed = stateStore.claimBatch(clock.instant());
        claimed.forEach(this::process);
        return claimed.size();
    }

    public int releaseStuckRequests() {
        Instant now = clock.instant();
        Instant threshold = now.minus(properties.processingLease());
        int released = 0;
        for (UUID id : repository.findStuckProcessingIds(threshold)) {
            try {
                if (stateStore.releaseStuck(id, threshold, now)) {
                    released++;
                    log.warn("Solicitação {} estava presa em PROCESSING e foi liberada para nova tentativa", id);
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Solicitação {} foi atualizada por outro processo durante a reconciliação", id);
            }
        }
        return released;
    }

    private void process(ClaimedRequest request) {
        MDC.put("requestId", request.id().toString());
        MDC.put(CorrelationIdFilter.MDC_KEY, request.correlationId());
        try {
            ProviderResult result = providerClient.activate(
                    new ProviderClient.ActivationCommand(request.id(), request.customerId(), request.productCode()),
                    request.correlationId());
            stateStore.applyResult(request.id(), result, clock.instant());
            log.info("Solicitação {} tentativa {}: {}", request.id(), request.attempt(), result);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Solicitação {} foi atualizada por outro processo; resultado descartado", request.id());
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao processar a solicitação {}", request.id(), e);
        } finally {
            MDC.remove("requestId");
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
