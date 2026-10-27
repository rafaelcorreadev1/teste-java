package com.deroyque.activation.request.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;

@IntegrationTest
class ConcurrentCreationIT {

    private static final int THREADS = 10;

    @Autowired
    ActivationRequestService service;

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @RepeatedTest(3)
    void identicalConcurrentRequestsCreateExactlyOneRecord() throws Exception {
        NewActivationRequest command = new NewActivationRequest("EXT-777", "CUST-1", "INTERNET-500");
        CountDownLatch start = new CountDownLatch(1);
        Callable<CreateResult> task = () -> {
            start.await();
            return service.create(command);
        };

        List<CreateResult> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<CreateResult>> futures = IntStream.range(0, THREADS).mapToObj(i -> executor.submit(task)).toList();
            start.countDown();
            results = futures.stream().map(ConcurrentCreationIT::await).toList();
        }

        assertThat(results).filteredOn(CreateResult.Created.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(CreateResult.Existing.class::isInstance).hasSize(THREADS - 1);
        assertThat(results).extracting(r -> r.request().getId()).containsOnly(results.getFirst().request().getId());
        assertThat(repository.count()).isEqualTo(1);
    }

    private static CreateResult await(Future<CreateResult> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
