package com.deroyque.activation.request.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.deroyque.activation.request.app.ActivationRequestService;
import com.deroyque.activation.request.app.CreateResult;
import com.deroyque.activation.request.domain.ActivationStatus;
import com.deroyque.activation.shared.security.ConsumerIdentity;

@RestController
@RequestMapping("/api/v1/requests")
class ActivationRequestController {

    private final ActivationRequestService service;

    ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ActivationRequestResponse> create(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateActivationRequest body) {
        return switch (service.create(body.toCommand(ConsumerIdentity.of(token)))) {
            case CreateResult.Created created -> ResponseEntity
                    .created(locationOf(created.request().getId()))
                    .body(ActivationRequestResponse.from(created.request()));
            case CreateResult.Existing existing -> ResponseEntity
                    .ok(ActivationRequestResponse.from(existing.request()));
        };
    }

    @GetMapping
    PageResponse<ActivationRequestResponse> list(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(required = false) @Size(max = 64) @Pattern(regexp = CreateActivationRequest.IDENTIFIER)
            String customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
                service.list(ConsumerIdentity.of(token), status, customerId, page, size),
                ActivationRequestResponse::from);
    }

    @GetMapping("/{id}")
    ActivationRequestResponse findById(@AuthenticationPrincipal Jwt token, @PathVariable UUID id) {
        return ActivationRequestResponse.from(service.findById(id, ConsumerIdentity.of(token)));
    }

    private static URI locationOf(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(id).toUri();
    }
}
