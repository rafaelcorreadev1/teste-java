package com.deroyque.activation.request.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "activation_request")
public class ActivationRequest {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivationRequest() {
    }

    private ActivationRequest(String externalId, String customerId, String productCode, Instant now) {
        this.id = UUID.randomUUID();
        this.externalId = Objects.requireNonNull(externalId);
        this.customerId = Objects.requireNonNull(customerId);
        this.productCode = Objects.requireNonNull(productCode);
        this.status = ActivationStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ActivationRequest create(String externalId, String customerId, String productCode, Instant now) {
        return new ActivationRequest(externalId, customerId, productCode, now);
    }

    /** A tentativa é contada aqui, e não no resultado, para que envios interrompidos por queda também a consumam. */
    public void startProcessing(Instant now) {
        transitionTo(ActivationStatus.PROCESSING, now);
        attempts++;
        processingStartedAt = now;
    }

    public void complete(Instant now) {
        transitionTo(ActivationStatus.COMPLETED, now);
        processingStartedAt = null;
    }

    public void fail(String reason, Instant now) {
        transitionTo(ActivationStatus.FAILED, now);
        failureReason = Objects.requireNonNull(reason);
        processingStartedAt = null;
    }

    public void returnToPending(Instant nextAttemptAt, Instant now) {
        transitionTo(ActivationStatus.PENDING, now);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        processingStartedAt = null;
    }

    public boolean hasSameContentAs(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    private void transitionTo(ActivationStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        status = target;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
