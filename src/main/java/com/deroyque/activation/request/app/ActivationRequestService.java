package com.deroyque.activation.request.app;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.shared.persistence.ConstraintViolations;

@Service
public class ActivationRequestService {

    private final ActivationRequestWriter writer;
    private final ActivationRequestRepository repository;
    private final Clock clock;

    ActivationRequestService(ActivationRequestWriter writer, ActivationRequestRepository repository, Clock clock) {
        this.writer = writer;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Não é transacional de propósito: após a violação da constraint a transação do insert
     * fica marcada para rollback, e a leitura do registro vencedor precisa de uma transação nova.
     */
    public CreateResult create(NewActivationRequest command) {
        ActivationRequest request = ActivationRequest.create(
                command.externalId(), command.customerId(), command.productCode(), clock.instant());
        try {
            return new CreateResult.Created(writer.insert(request));
        } catch (DataIntegrityViolationException e) {
            if (!ConstraintViolations.isViolationOf(e, ActivationRequestRepository.EXTERNAL_ID_CONSTRAINT)) {
                throw e;
            }
            return resolveDuplicate(command, e);
        }
    }

    public ActivationRequest findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    private CreateResult resolveDuplicate(NewActivationRequest command, DataIntegrityViolationException cause) {
        ActivationRequest existing = repository.findByExternalId(command.externalId()).orElseThrow(() -> cause);
        if (!existing.hasSameContentAs(command.customerId(), command.productCode())) {
            throw new DuplicateRequestException(command.externalId());
        }
        return new CreateResult.Existing(existing);
    }
}
