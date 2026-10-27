package com.deroyque.activation.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.databind.ObjectMapper;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(DataSize.ofBytes(100), new ObjectMapper());

    @Test
    void chunkedBodyAboveLimitIsInterruptedWhileReading() {
        MockHttpServletRequest request = chunked(new byte[150]);
        MockFilterChain readsWholeBody = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                req.getInputStream().readAllBytes();
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), readsWholeBody))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void chunkedBodyWithinLimitIsReadNormally() throws Exception {
        MockHttpServletRequest request = chunked(new byte[80]);
        int[] read = new int[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                read[0] = req.getInputStream().readAllBytes().length;
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(read[0]).isEqualTo(80);
    }

    @Test
    void declaredContentLengthAboveLimitIsRejectedBeforeReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/requests");
        request.setContent(new byte[150]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletRequest chunked(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/requests") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }
}
