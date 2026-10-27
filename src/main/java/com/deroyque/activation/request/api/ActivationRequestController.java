package com.deroyque.activation.request.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.deroyque.activation.request.app.ActivationRequestService;
import com.deroyque.activation.request.app.CreateResult;

@RestController
@RequestMapping("/api/v1/requests")
class ActivationRequestController {

    private final ActivationRequestService service;

    ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequest body) {
        return switch (service.create(body.toCommand())) {
            case CreateResult.Created created -> ResponseEntity
                    .created(locationOf(created.request().getId()))
                    .body(ActivationRequestResponse.from(created.request()));
            case CreateResult.Existing existing -> ResponseEntity
                    .ok(ActivationRequestResponse.from(existing.request()));
        };
    }

    @GetMapping("/{id}")
    ActivationRequestResponse findById(@PathVariable UUID id) {
        return ActivationRequestResponse.from(service.findById(id));
    }

    private static URI locationOf(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(id).toUri();
    }
}
