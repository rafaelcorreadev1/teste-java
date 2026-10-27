package com.deroyque.activation.request.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;

public interface ActivationRequestRepository
        extends JpaRepository<ActivationRequest, UUID>, JpaSpecificationExecutor<ActivationRequest> {

    String EXTERNAL_ID_CONSTRAINT = "uk_activation_request_client_external_id";

    Optional<ActivationRequest> findByIdAndClientId(UUID id, String clientId);

    Optional<ActivationRequest> findByClientIdAndExternalId(String clientId, String externalId);

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

    @Query("select r.status as status, count(r) as total from ActivationRequest r group by r.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        ActivationStatus getStatus();

        long getTotal();
    }
}
