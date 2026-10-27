package com.deroyque.activation.shared.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deroyque.activation.support.IntegrationTest;

@IntegrationTest
class ReadinessIT {

    @Autowired
    MockMvc mvc;

    @TestBean(name = "dbHealthContributor", methodName = "databaseDown")
    HealthContributor databaseHealth;

    static HealthContributor databaseDown() {
        return (HealthIndicator) () -> Health.down().withDetail("simulated", true).build();
    }

    @Test
    void databaseDownTakesInstanceOutOfTrafficButKeepsItAlive() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }
}
