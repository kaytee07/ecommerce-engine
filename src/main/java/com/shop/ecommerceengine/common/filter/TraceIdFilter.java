package com.shop.ecommerceengine.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that generates a unique traceId for each request and adds it to MDC for logging.
 * The traceId is also added to the response header for client-side correlation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        log.debug("Request started: {} {} [traceId={}]",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            log.debug("Request completed: {} {} [traceId={}]",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    traceId);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
