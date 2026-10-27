package com.deroyque.activation.request.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.deroyque.activation.request.domain.ActivationRequest;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {

    String EXTERNAL_ID_CONSTRAINT = "uk_activation_request_external_id";

    Optional<ActivationRequest> findByExternalId(String externalId);

    /** SKIP LOCKED faz instâncias concorrentes pularem as linhas já reivindicadas em vez de esperar por elas. */
    @Query(value = """
            select * from activation_request
             where status = 'PENDING' and next_attempt_at <= :now
             order by next_attempt_at
             limit :limit
               for update skip locked
            """, nativeQuery = true)
    List<ActivationRequest> lockDuePending(Instant now, int limit);

    @Query("""
            select r.id from ActivationRequest r
             where r.status = com.deroyque.activation.request.domain.ActivationStatus.PROCESSING
               and r.processingStartedAt < :threshold
            """)
    List<UUID> findStuckProcessingIds(Instant threshold);
}
