package com.deroyque.activation.shared.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.deroyque.activation.shared.error.ProblemTypes;

public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(long limitBytes) {
        super("Corpo da requisição acima de %d bytes".formatted(limitBytes));
    }

    public static boolean isCauseOf(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PayloadTooLargeException) {
                return true;
            }
        }
        return false;
    }

    public static ProblemDetail problem() {
        return ProblemTypes.problem(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Corpo muito grande",
                "O corpo da requisição excede o tamanho máximo permitido.");
    }
}
