package com.deroyque.activation.request.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "activation.worker.enabled", havingValue = "true")
class ActivationScheduler {

    private final ActivationDispatcher dispatcher;

    ActivationScheduler(ActivationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
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
