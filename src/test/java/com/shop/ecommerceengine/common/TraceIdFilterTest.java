package com.shop.ecommerceengine.common;

import com.shop.ecommerceengine.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
    }

    @Test
    @DisplayName("Should generate traceId when not provided in request header")
    void shouldGenerateTraceIdWhenNotProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        traceIdFilter.doFilter(request, response, filterChain);

        String responseTraceId = response.getHeader("X-Trace-Id");
        assertThat(responseTraceId).isNotNull();
        assertThat(responseTraceId).isNotBlank();
        // UUID format validation
        assertThat(responseTraceId).matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    }

    @Test
    @DisplayName("Should use provided traceId from request header")
    void shouldUseProvidedTraceId() throws Exception {
        String providedTraceId = "custom-trace-id-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", providedTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        traceIdFilter.doFilter(request, response, filterChain);

        String responseTraceId = response.getHeader("X-Trace-Id");
        assertThat(responseTraceId).isEqualTo(providedTraceId);
    }

    @Test
    @DisplayName("Should clear MDC after request processing")
    void shouldClearMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        traceIdFilter.doFilter(request, response, filterChain);

        // MDC should be cleared after the filter completes
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("Should add traceId to response header")
    void shouldAddTraceIdToResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        traceIdFilter.doFilter(request, response, filterChain);

        assertThat(response.containsHeader("X-Trace-Id")).isTrue();
    }
}
