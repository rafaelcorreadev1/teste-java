package com.deroyque.activation.request.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.shared.persistence.ConstraintViolations;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;

@IntegrationTest
class ActivationRequestRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @Test
    void duplicateExternalIdViolatesNamedConstraint() {
        repository.saveAndFlush(ActivationRequest.create("sys-a", "EXT-1", "CUST-1", "INTERNET-500", NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(ActivationRequest.create("sys-a", "EXT-1", "CUST-2", "TV-HD", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(error -> assertThat(
                        ConstraintViolations.isViolationOf(error, ActivationRequestRepository.EXTERNAL_ID_CONSTRAINT))
                        .isTrue());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameExternalIdIsAllowedForDifferentClients() {
        repository.saveAndFlush(ActivationRequest.create("sys-a", "EXT-1", "CUST-1", "INTERNET-500", NOW));
        repository.saveAndFlush(ActivationRequest.create("sys-b", "EXT-1", "CUST-9", "TV-HD", NOW));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findsByExternalId() {
        ActivationRequest saved = repository.saveAndFlush(ActivationRequest.create("sys-a", "EXT-1", "CUST-1", "INTERNET-500", NOW));

        assertThat(repository.findByClientIdAndExternalId("sys-a", "EXT-1")).get()
                .extracting(ActivationRequest::getId).isEqualTo(saved.getId());
        assertThat(repository.findByClientIdAndExternalId("sys-a", "EXT-404")).isEmpty();
    }

    @Test
    void staleVersionCannotOverwriteConcurrentUpdate() {
        ActivationRequest request = ActivationRequest.create("sys-a", "EXT-1", "CUST-1", "INTERNET-500", NOW);
        request.startProcessing(NOW);
        repository.saveAndFlush(request);

        ActivationRequest firstCopy = repository.findById(request.getId()).orElseThrow();
        ActivationRequest staleCopy = repository.findById(request.getId()).orElseThrow();

        firstCopy.complete(NOW.plusSeconds(1));
        repository.saveAndFlush(firstCopy);

        staleCopy.returnToPending(NOW.plusSeconds(60), NOW.plusSeconds(2));
        assertThatThrownBy(() -> repository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(repository.findById(request.getId())).get()
                .extracting(ActivationRequest::getStatus).isEqualTo(ActivationStatus.COMPLETED);
    }
}
