package com.deroyque.activation.shared.error;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.deroyque.activation.shared.web.CorrelationIdFilter;

public final class ProblemTypes {

    public static final String BASE_URI = "https://api.deroyque.com/problems/";

    private ProblemTypes() {
    }

    public static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE_URI + slug));
        problem.setTitle(title);
        return withTraceId(problem);
    }

    public static ProblemDetail withTraceId(ProblemDetail problem) {
        problem.setProperty("traceId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
