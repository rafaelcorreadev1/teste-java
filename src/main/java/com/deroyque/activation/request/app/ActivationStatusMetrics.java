package com.deroyque.activation.request.app;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.request.infra.ActivationRequestRepository;

/**
 * Quantidade de solicitações por status. É recalculada periodicamente, e não a cada scrape,
 * para que o Prometheus não dispare um count no banco a cada coleta.
 */
@Component
public class ActivationStatusMetrics {

    private final ActivationRequestRepository repository;
    private final Map<ActivationStatus, AtomicLong> counts = new EnumMap<>(ActivationStatus.class);

    ActivationStatusMetrics(ActivationRequestRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        Arrays.stream(ActivationStatus.values()).forEach(status -> {
            AtomicLong count = new AtomicLong();
            counts.put(status, count);
            Gauge.builder("activation.requests", count, AtomicLong::get)
                    .description("Solicitações de ativação por status")
                    .tag("status", status.name())
                    .register(meterRegistry);
        });
    }

    public void refresh() {
        counts.values().forEach(count -> count.set(0));
        repository.countByStatus().forEach(row -> counts.get(row.getStatus()).set(row.getTotal()));
    }
}
