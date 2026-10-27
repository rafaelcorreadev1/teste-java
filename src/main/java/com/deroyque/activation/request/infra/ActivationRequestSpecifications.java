package com.deroyque.activation.request.infra;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.domain.ActivationStatus;

public final class ActivationRequestSpecifications {

    private ActivationRequestSpecifications() {
    }

    public static Specification<ActivationRequest> listing(String clientId, ActivationStatus status, String customerId) {
        List<Specification<ActivationRequest>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.equal(root.get("clientId"), clientId));
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (customerId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        return Specification.allOf(filters);
    }
}
