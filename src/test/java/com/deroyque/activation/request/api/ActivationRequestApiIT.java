package com.deroyque.activation.request.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class ActivationRequestApiIT {

    private static final String BODY = """
            {"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"%s"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @Test
    void fullLifecycleAgainstRealDatabase() throws Exception {
        String created = mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("INTERNET-500")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String createdAt = JsonPath.read(created, "$.createdAt");

        mvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("INTERNET-500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("INTERNET-1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/duplicate-request")));

        mvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/not-found")));
    }

    @Test
    void invalidRequestCreatesNothing() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"EXT-1\"}"))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }
}
