package com.deroyque.activation.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

@TestComponent
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute("truncate table activation_request");
    }

    /** Simula a passagem do tempo até o backoff vencer, sem depender de sleep. */
    public void makeAllPendingDue() {
        jdbc.update("update activation_request set next_attempt_at = timestamptz '2000-01-01Z' where status = 'PENDING'");
    }

    /** Simula uma instância que caiu no meio do envio, deixando a linha em PROCESSING há muito tempo. */
    public void ageProcessingStart() {
        jdbc.update("update activation_request set processing_started_at = timestamptz '2000-01-01Z' where status = 'PROCESSING'");
    }
}
