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
        // O flush força o INSERT aqui, para a violação da constraint estourar neste método e não no commit.
        return repository.saveAndFlush(request);
    }
}
