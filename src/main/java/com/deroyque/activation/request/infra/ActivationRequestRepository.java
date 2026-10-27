package com.deroyque.activation.request.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deroyque.activation.request.domain.ActivationRequest;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {

    String EXTERNAL_ID_CONSTRAINT = "uk_activation_request_external_id";

    Optional<ActivationRequest> findByExternalId(String externalId);
}
