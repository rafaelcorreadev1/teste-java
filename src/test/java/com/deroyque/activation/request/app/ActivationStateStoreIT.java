package com.deroyque.activation.request.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;

@IntegrationTest
class ActivationStateStoreIT {

    @Autowired
    ActivationStateStore stateStore;

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    Clock clock;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        Instant created = clock.instant().minusSeconds(60);
        IntStream.range(0, 20).forEach(i ->
                repository.save(ActivationRequest.create("EXT-" + i, "CUST-1", "INTERNET-500", created)));
    }

    @Test
    void concurrentClaimSkipsRowsLockedByAnotherTransaction() throws Exception {
        CountDownLatch otherHoldsLocks = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<List<UUID>> other = CompletableFuture.supplyAsync(() ->
                transactionTemplate.execute(status -> {
                    List<UUID> locked = repository.lockDuePending(clock.instant(), 10).stream()
                            .map(ActivationRequest::getId).toList();
                    otherHoldsLocks.countDown();
                    await(release);
                    return locked;
                }));
        assertThat(otherHoldsLocks.await(10, TimeUnit.SECONDS)).isTrue();

        List<UUID> claimed = CompletableFuture.supplyAsync(() -> stateStore.claimBatch(clock.instant()))
                .get(5, TimeUnit.SECONDS)
                .stream().map(ClaimedRequest::id).toList();

        release.countDown();
        List<UUID> lockedByOther = other.get(10, TimeUnit.SECONDS);

        assertThat(claimed).hasSize(10).doesNotContainAnyElementsOf(lockedByOther);
        assertThat(lockedByOther).hasSize(10);
    }

    @Test
    void claimMovesToProcessingAndCountsAttempt() {
        List<ClaimedRequest> claimed = stateStore.claimBatch(clock.instant());

        assertThat(claimed).hasSize(10).allSatisfy(c -> assertThat(c.attempt()).isEqualTo(1));
        assertThat(repository.findAll())
                .filteredOn(r -> r.getStatus() == ActivationStatus.PROCESSING).hasSize(10);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
