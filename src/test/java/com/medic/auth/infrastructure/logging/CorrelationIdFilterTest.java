package com.medic.auth.infrastructure.logging;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_ShouldGenerateCorrelationId_WhenHeaderNotPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String correlationId = response.getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertFalse(correlationId.isEmpty());
    }

    @Test
    void doFilter_ShouldPreserveCorrelationId_WhenHeaderProvided() throws Exception {
        String expectedId = "test-correlation-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", expectedId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(expectedId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void doFilter_ShouldSetCorrelationIdInMdc_DuringFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "my-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] mdcDuringFilter = {null};
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res)
                    throws IOException, ServletException {
                mdcDuringFilter[0] = MDC.get("correlationId");
                super.doFilter(req, res);
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals("my-correlation-id", mdcDuringFilter[0]);
    }

    @Test
    void doFilter_ShouldClearMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(MDC.get("correlationId"));
    }

    @Test
    void doFilter_ShouldGenerateUniqueIds_ForEachRequest() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, res1, new MockFilterChain());

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, res2, new MockFilterChain());

        assertNotEquals(res1.getHeader("X-Correlation-Id"), res2.getHeader("X-Correlation-Id"));
    }
}
