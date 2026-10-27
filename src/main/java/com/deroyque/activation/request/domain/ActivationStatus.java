package com.deroyque.activation.request.domain;

import java.util.Set;

public enum ActivationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public Set<ActivationStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(PROCESSING);
            case PROCESSING -> Set.of(COMPLETED, FAILED, PENDING);
            case COMPLETED, FAILED -> Set.of();
        };
    }

    public boolean canTransitionTo(ActivationStatus target) {
        return allowedTargets().contains(target);
    }
}
