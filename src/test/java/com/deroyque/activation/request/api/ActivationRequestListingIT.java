package com.deroyque.activation.request.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.request.infra.ActivationRequestRepository;
import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;
import com.deroyque.activation.support.TestJwts;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class ActivationRequestListingIT {

    private static final Instant BASE = Instant.parse("2026-10-27T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ActivationRequestRepository repository;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    @Test
    void filtersByStatus() throws Exception {
        for (int i = 0; i < 3; i++) {
            save(failed(TestJwts.SYSTEM_A, "FAIL-" + i, "CUST-1", i));
        }
        for (int i = 0; i < 5; i++) {
            save(completed(TestJwts.SYSTEM_A, "OK-" + i, "CUST-1", 10 + i));
        }

        mvc.perform(get("/api/v1/requests").param("status", "FAILED").with(TestJwts.fullAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("FAILED"))));
    }

    @Test
    void combinesFilters() throws Exception {
        save(ActivationRequest.create(TestJwts.SYSTEM_A, "A", "CUST-1", "P", BASE));
        save(ActivationRequest.create(TestJwts.SYSTEM_A, "B", "CUST-2", "P", BASE));
        save(completed(TestJwts.SYSTEM_A, "C", "CUST-1", 1));

        mvc.perform(get("/api/v1/requests").param("status", "PENDING").param("customerId", "CUST-1")
                        .with(TestJwts.fullAccess()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].externalId").value("A"));
    }

    @Test
    void paginatesNewestFirst() throws Exception {
        for (int i = 0; i < 25; i++) {
            save(ActivationRequest.create(TestJwts.SYSTEM_A, "EXT-" + i, "CUST-1", "P", BASE.plusSeconds(i)));
        }

        String body = mvc.perform(get("/api/v1/requests").param("page", "1").param("size", "10")
                        .with(TestJwts.fullAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andReturn().getResponse().getContentAsString();

        List<String> externalIds = JsonPath.read(body, "$.content[*].externalId");
        assertThat(externalIds).containsExactly(
                "EXT-14", "EXT-13", "EXT-12", "EXT-11", "EXT-10", "EXT-9", "EXT-8", "EXT-7", "EXT-6", "EXT-5");
    }

    @Test
    void listsOnlyCallerRequests() throws Exception {
        for (int i = 0; i < 2; i++) {
            save(ActivationRequest.create(TestJwts.SYSTEM_A, "A-" + i, "CUST-1", "P", BASE));
        }
        for (int i = 0; i < 4; i++) {
            save(ActivationRequest.create(TestJwts.SYSTEM_B, "B-" + i, "CUST-1", "P", BASE));
        }

        mvc.perform(get("/api/v1/requests").with(TestJwts.fullAccess(TestJwts.SYSTEM_A)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listingQueriesCanUseCompositeIndexes() {
        assertThat(explain("select * from activation_request where client_id = 'sys-a' and status = 'FAILED' "
                + "order by created_at desc limit 20")).contains("ix_activation_request_client_status");
        assertThat(explain("select * from activation_request where client_id = 'sys-a' and customer_id = 'CUST-1' "
                + "order by created_at desc limit 20")).contains("ix_activation_request_client_customer");
    }

    /** Com a tabela quase vazia o planner sempre prefere seq scan; desligá-lo mostra se o índice atende à consulta. */
    private String explain(String sql) {
        return transactionTemplate.execute(status -> {
            jdbc.execute("set local enable_seqscan = off");
            return String.join("\n", jdbc.queryForList("explain " + sql, String.class));
        });
    }

    private ActivationRequest failed(String client, String externalId, String customer, int offsetSeconds) {
        ActivationRequest request = ActivationRequest.create(client, externalId, customer, "P", BASE.plusSeconds(offsetSeconds));
        request.startProcessing(BASE);
        request.fail("PROVIDER_REJECTED_400", BASE);
        return request;
    }

    private ActivationRequest completed(String client, String externalId, String customer, int offsetSeconds) {
        ActivationRequest request = ActivationRequest.create(client, externalId, customer, "P", BASE.plusSeconds(offsetSeconds));
        request.startProcessing(BASE);
        request.complete(BASE);
        return request;
    }

    private void save(ActivationRequest request) {
        repository.saveAndFlush(request);
    }
}
