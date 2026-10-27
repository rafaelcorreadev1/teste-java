package com.deroyque.activation.shared.security;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.deroyque.activation.shared.error.ProblemTypes;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    ProblemDetailsSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, ex);
        write(response, request, ProblemTypes.problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Não autenticado",
                "É necessário um token de acesso válido."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException, ServletException {
        bearerAccessDenied.handle(request, response, ex);
        write(response, request, ProblemTypes.problem(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado",
                "O token não possui o escopo necessário para esta operação."));
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ProblemDetail problem)
            throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
