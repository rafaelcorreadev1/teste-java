package com.deroyque.activation.request.app;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.shared.persistence.ConstraintViolations;

@Service
public class ActivationRequestService {

    private static final Logger log = LoggerFactory.getLogger(ActivationRequestService.class);

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
        ActivationRequest request = ActivationRequest.create(command.clientId(),
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

    /** Solicitação de outro cliente responde como inexistente, para não revelar que o id existe. */
    public ActivationRequest findById(UUID id, String clientId) {
        return repository.findByIdAndClientId(id, clientId).orElseThrow(() -> {
            log.debug("Solicitação {} não encontrada para o cliente {}", id, clientId);
            return new ActivationRequestNotFoundException(id);
        });
    }

    private CreateResult resolveDuplicate(NewActivationRequest command, DataIntegrityViolationException cause) {
        ActivationRequest existing = repository.findByClientIdAndExternalId(command.clientId(), command.externalId())
                .orElseThrow(() -> cause);
        if (!existing.hasSameContentAs(command.customerId(), command.productCode())) {
            throw new DuplicateRequestException(command.externalId());
        }
        return new CreateResult.Existing(existing);
    }
}
