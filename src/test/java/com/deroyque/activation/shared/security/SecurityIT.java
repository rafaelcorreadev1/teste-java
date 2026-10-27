package com.deroyque.activation.shared.security;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.deroyque.activation.support.DatabaseCleaner;
import com.deroyque.activation.support.IntegrationTest;
import com.deroyque.activation.support.TestJwts;
import com.deroyque.activation.support.TestTokenIssuer;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;

/** Tokens reais assinados, validados pelo JwtDecoder configurado na aplicação (sem o atalho jwt() do MockMvc). */
@IntegrationTest
class SecurityIT {

    private static final String BODY = """
            {"externalId":"EXT-1","customerId":"CUST-1","productCode":"INTERNET-500"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    WireMockServer externalServices;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        TestTokenIssuer.publishJwks(externalServices);
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().bearer())
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/unauthorized")));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().expiresAt(Instant.now().minusSeconds(120)).bearer())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithinClockSkewIsAccepted() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().expiresAt(Instant.now().minusSeconds(10)).bearer())
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().audience("billing-service").bearer())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().issuer("https://evil.example").bearer())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signatureFromUnknownKeyIsRejected() throws Exception {
        getRequest(UUID.randomUUID(), TestTokenIssuer.token().signedWithUnknownKey().bearer())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopeFromRealTokenIsEnforced() throws Exception {
        create(TestTokenIssuer.token().scope("activation:read").bearer(), BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/forbidden")));
    }

    @Test
    void clientCannotSeeAnotherClientsRequest() throws Exception {
        String created = create(TestTokenIssuer.token().clientId(TestJwts.SYSTEM_A).bearer(), BODY)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));

        getRequest(id, TestTokenIssuer.token().clientId(TestJwts.SYSTEM_A).bearer()).andExpect(status().isOk());
        getRequest(id, TestTokenIssuer.token().clientId(TestJwts.SYSTEM_B).bearer())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/not-found")));
    }

    @Test
    void sameExternalIdIsIndependentPerClient() throws Exception {
        create(TestTokenIssuer.token().clientId(TestJwts.SYSTEM_A).bearer(), BODY).andExpect(status().isCreated());
        create(TestTokenIssuer.token().clientId(TestJwts.SYSTEM_B).bearer(), BODY.replace("INTERNET-500", "TV-HD"))
                .andExpect(status().isCreated());
    }

    @Test
    void healthProbesArePublicWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    private ResultActions getRequest(UUID id, String bearer) throws Exception {
        return mvc.perform(get("/api/v1/requests/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions create(String bearer, String body) throws Exception {
        return mvc.perform(post("/api/v1/requests").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
