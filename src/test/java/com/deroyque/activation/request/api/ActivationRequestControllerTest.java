package com.deroyque.activation.request.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.deroyque.activation.config.SecurityConfig;
import com.deroyque.activation.request.app.ActivationRequestNotFoundException;
import com.deroyque.activation.request.app.ActivationRequestService;
import com.deroyque.activation.request.app.CreateResult;
import com.deroyque.activation.request.app.DuplicateRequestException;
import com.deroyque.activation.request.app.NewActivationRequest;
import com.deroyque.activation.request.domain.ActivationRequest;
import com.deroyque.activation.shared.security.ProblemDetailsSecurityHandler;
import com.deroyque.activation.shared.web.CorrelationIdFilter;
import com.deroyque.activation.shared.web.RequestSizeLimitFilter;
import com.deroyque.activation.support.TestJwts;

@WebMvcTest(ActivationRequestController.class)
@Import({SecurityConfig.class, ProblemDetailsSecurityHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class ActivationRequestControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final String VALID_BODY = """
            {"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}
            """;

    @Autowired
    WebApplicationContext context;

    @Autowired
    MockMvc anonymousMvc;

    /** Autenticado como sys-a com leitura e escrita; os testes de segurança usam {@link #anonymousMvc}. */
    MockMvc mvc;

    @MockitoBean
    ActivationRequestService service;

    private final ActivationRequest request = ActivationRequest.create("sys-a", "EXT-12345", "CUST-10001", "INTERNET-500", NOW);

    @BeforeEach
    void authenticateAsSystemA(@Autowired CorrelationIdFilter correlationIdFilter,
            @Autowired RequestSizeLimitFilter requestSizeLimitFilter) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(correlationIdFilter, requestSizeLimitFilter)
                .apply(springSecurity())
                .defaultRequest(get("/").with(TestJwts.fullAccess()))
                .build();
    }

    @Test
    void requestWithoutTokenReturns401Problem() throws Exception {
        anonymousMvc.perform(get("/api/v1/requests/{id}", request.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("/problems/unauthorized")))
                .andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())));
        verifyNoInteractions(service);
    }

    @Test
    void readOnlyClientCannotCreate() throws Exception {
        anonymousMvc.perform(post("/api/v1/requests").with(TestJwts.client("sys-a", "activation:read"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/forbidden")));
        verifyNoInteractions(service);
    }

    @Test
    void writeOnlyClientCannotRead() throws Exception {
        anonymousMvc.perform(get("/api/v1/requests/{id}", request.getId()).with(TestJwts.client("sys-a", "activation:write")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void writeScopeCanCreateAndOwnerComesFromToken() throws Exception {
        given(service.create(any())).willReturn(new CreateResult.Created(request));

        anonymousMvc.perform(post("/api/v1/requests").with(TestJwts.client("sys-b", "activation:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(service).create(new NewActivationRequest("sys-b", "EXT-12345", "CUST-10001", "INTERNET-500"));
    }

    @Test
    void oversizedBodyReturns413() throws Exception {
        String body = """
                {"externalId":"EXT-1","customerId":"CUST-1","productCode":"P","padding":"%s"}
                """.formatted("x".repeat(20 * 1024));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/payload-too-large")));
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(service.create(any())).willReturn(new CreateResult.Created(request));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/requests/" + request.getId())))
                .andExpect(jsonPath("$.id").value(request.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-27T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-27T12:00:00Z"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.failureReason").isEmpty())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void retryOfExistingReturns200WithoutLocation() throws Exception {
        given(service.create(any())).willReturn(new CreateResult.Existing(request));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(request.getId().toString()));
    }

    @Test
    void missingFieldsReturnValidationProblem() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"EXT-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("/problems/validation-error")))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests"))
                .andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("customerId", "productCode")));
        verifyNoInteractions(service);
    }

    @Test
    void fieldAboveMaxLengthIsRejected() throws Exception {
        String body = """
                {"externalId":"EXT-1","customerId":"CUST-1","productCode":"%s"}
                """.formatted("P".repeat(65));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("productCode")));
    }

    @Test
    void fieldWithInvalidCharactersIsRejected() throws Exception {
        String body = """
                {"externalId":"EXT 123;DROP","customerId":"CUST-1","productCode":"INTERNET-500"}
                """;

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("externalId")));
    }

    @Test
    void malformedJsonReturnsMalformedRequestProblem() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\": \"EXT-1\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/malformed-request")))
                .andExpect(jsonPath("$.detail").value(not(containsString("JSON parse error"))));
    }

    @Test
    void duplicateWithDifferentContentReturns409() throws Exception {
        given(service.create(any())).willThrow(new DuplicateRequestException("EXT-12345"));

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/problems/duplicate-request")));
    }

    @Test
    void getExistingReturns200() throws Exception {
        given(service.findById(request.getId(), "sys-a")).willReturn(request);

        mvc.perform(get("/api/v1/requests/{id}", request.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getFailedRequestExposesReasonAndAttempts() throws Exception {
        ActivationRequest failed = ActivationRequest.create("sys-a", "EXT-9", "CUST-1", "X-REJECT", NOW);
        failed.startProcessing(NOW);
        failed.fail("PROVIDER_REJECTED_400", NOW);
        given(service.findById(failed.getId(), "sys-a")).willReturn(failed);

        mvc.perform(get("/api/v1/requests/{id}", failed.getId()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("PROVIDER_REJECTED_400"))
                .andExpect(jsonPath("$.attempts").value(1));
    }

    @Test
    void getMissingReturnsNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.findById(id, "sys-a")).willThrow(new ActivationRequestNotFoundException(id));

        mvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("/problems/not-found")))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests/" + id))
                .andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/requests/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedErrorHidesInternalsAndLogsWithSameTraceId(CapturedOutput output) throws Exception {
        given(service.findById(any(), any())).willThrow(new IllegalStateException("select * from activation_request falhou"));

        MvcResult result = mvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID())
                        .header("X-Correlation-Id", "trace-abc-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Correlation-Id", "trace-abc-123"))
                .andExpect(jsonPath("$.type").value(endsWith("/problems/internal-error")))
                .andExpect(jsonPath("$.traceId").value("trace-abc-123"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "at com.", "select");
        assertThat(output).contains("[trace-abc-123]").contains("IllegalStateException");
    }

    @Test
    void generatesCorrelationIdWhenAbsentOrInvalid() throws Exception {
        given(service.findById(request.getId(), "sys-a")).willReturn(request);

        mvc.perform(get("/api/v1/requests/{id}", request.getId()))
                .andExpect(header().string("X-Correlation-Id", not(emptyOrNullString())));
        mvc.perform(get("/api/v1/requests/{id}", request.getId()).header("X-Correlation-Id", "bad\nvalue"))
                .andExpect(header().string("X-Correlation-Id", not("bad\nvalue")));
    }
}
