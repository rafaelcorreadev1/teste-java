package com.deroyque.activation.shared.error;

import static com.deroyque.activation.shared.error.ProblemTypes.problem;
import static com.deroyque.activation.shared.error.ProblemTypes.withTraceId;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.deroyque.activation.request.app.ActivationRequestNotFoundException;
import com.deroyque.activation.request.app.DuplicateRequestException;
import com.deroyque.activation.shared.web.PayloadTooLargeException;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos",
                "Um ou mais campos não passaram na validação.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (PayloadTooLargeException.isCauseOf(ex)) {
            ProblemDetail tooLarge = PayloadTooLargeException.problem();
            return handleExceptionInternal(ex, tooLarge, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "malformed-request", "Requisição malformada",
                "O corpo da requisição não é um JSON válido ou contém tipos incompatíveis.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String parameter = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos",
                "Um ou mais parâmetros têm formato inválido.");
        problem.setProperty("errors", List.of(Map.of("field", String.valueOf(parameter), "message", "formato inválido")));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(ActivationRequestNotFoundException.class)
    ProblemDetail handleNotFound(ActivationRequestNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Solicitação não encontrada",
                "Não existe solicitação com o id informado.");
    }

    @ExceptionHandler(DuplicateRequestException.class)
    ProblemDetail handleDuplicate(DuplicateRequestException ex) {
        return problem(HttpStatus.CONFLICT, "duplicate-request", "Solicitação duplicada", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Atualização concorrente",
                "A solicitação foi alterada por outro processo. Consulte o estado atual e tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro interno",
                "Ocorreu um erro inesperado. Informe o traceId ao suporte.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withTraceId(problem);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
