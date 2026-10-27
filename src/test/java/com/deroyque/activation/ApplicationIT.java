package com.deroyque.activation;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.deroyque.activation.support.IntegrationTest;

@IntegrationTest
class ApplicationIT {

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoadsAgainstRealPostgres() {
        String version = new JdbcTemplate(dataSource).queryForObject("select version()", String.class);

        assertThat(version).startsWith("PostgreSQL 16");
    }
}
