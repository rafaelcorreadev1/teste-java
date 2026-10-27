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
}
