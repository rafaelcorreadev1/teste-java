package com.deroyque.activation.request.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.infra.ActivationRequestRepository;

@Component
class ActivationRequestWriter {

    private final ActivationRequestRepository repository;

    ActivationRequestWriter(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivationRequest insert(ActivationRequest request) {
        return repository.saveAndFlush(request);
    }
}
