package com.deroyque.activation.request.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true")
class ActivationScheduler {

    private final ActivationDispatcher dispatcher;
    private final ActivationStatusMetrics statusMetrics;

    ActivationScheduler(ActivationDispatcher dispatcher, ActivationStatusMetrics statusMetrics) {
        this.dispatcher = dispatcher;
        this.statusMetrics = statusMetrics;
    }

    @Scheduled(fixedDelayString = "${activation.worker.metrics-interval:30s}")
    void refreshMetrics() {
        statusMetrics.refresh();
    }

    @Scheduled(fixedDelayString = "${activation.worker.poll-interval}")
    void dispatch() {
        dispatcher.dispatchDueRequests();
    }

    @Scheduled(fixedDelayString = "${activation.worker.reconcile-interval}")
    void reconcile() {
        dispatcher.releaseStuckRequests();
    }
}
