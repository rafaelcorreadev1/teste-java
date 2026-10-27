package com.deroyque.activation.request.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.infra.ActivationRequestRepository;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final NewActivationRequest COMMAND = new NewActivationRequest("EXT-1", "CUST-1", "INTERNET-500");

    @Mock
    ActivationRequestWriter writer;

    @Mock
    ActivationRequestRepository repository;

    ActivationRequestService service;

    @BeforeEach
    void setUp() {
        service = new ActivationRequestService(writer, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsPendingRequest() {
        willAnswer(invocation -> invocation.getArgument(0)).given(writer).insert(any());

        CreateResult result = service.create(COMMAND);

        assertThat(result).isInstanceOf(CreateResult.Created.class);
        assertThat(result.request().getExternalId()).isEqualTo("EXT-1");
        assertThat(result.request().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void retryWithSameContentReturnsExisting() {
        ActivationRequest existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        given(writer.insert(any())).willThrow(violationOf(ActivationRequestRepository.EXTERNAL_ID_CONSTRAINT));
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.of(existing));

        CreateResult result = service.create(COMMAND);

        assertThat(result).isEqualTo(new CreateResult.Existing(existing));
    }

    @Test
    void sameExternalIdWithDifferentContentIsRejected() {
        ActivationRequest existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-1000", NOW);
        given(writer.insert(any())).willThrow(violationOf(ActivationRequestRepository.EXTERNAL_ID_CONSTRAINT));
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(COMMAND)).isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    void otherIntegrityViolationsPropagate() {
        DataIntegrityViolationException otherViolation = violationOf("ck_activation_request_status");
        given(writer.insert(any())).willThrow(otherViolation);

        assertThatThrownBy(() -> service.create(COMMAND)).isSameAs(otherViolation);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ActivationRequestNotFoundException.class);
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException("violação",
                new ConstraintViolationException("violação", new SQLException("23505"), constraint));
    }
}
