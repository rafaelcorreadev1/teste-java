package com.deroyque.activation.request.app;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.request.infra.ActivationRequestSpecifications;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.shared.persistence.ConstraintViolations;
import com.deroyque.activation.shared.web.CorrelationIdFilter;

@Service
public class ActivationRequestService {

    private static final Logger log = LoggerFactory.getLogger(ActivationRequestService.class);

    /** O desempate por id evita itens repetidos ou pulados entre páginas quando há createdAt iguais. */
    private static final Sort LISTING_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

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
        request.traceWith(MDC.get(CorrelationIdFilter.MDC_KEY));
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

    public Page<ActivationRequest> list(String clientId, ActivationStatus status, String customerId, int page, int size) {
        return repository.findAll(ActivationRequestSpecifications.listing(clientId, status, customerId),
                PageRequest.of(page, size, LISTING_ORDER));
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
