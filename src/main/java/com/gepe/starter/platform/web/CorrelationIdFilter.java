package com.gepe.starter.platform.web;

import com.gepe.starter.platform.logging.MdcKeys;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates every log line of a request through the MDC {@code requestId} (see
 * §7 of {@code agents.md}).
 *
 * <p>Reads the inbound {@value #CORRELATION_ID_HEADER} header when present and
 * non-blank, otherwise generates a fresh {@link UUID}; stores the value in the
 * MDC under {@link MdcKeys#REQUEST_ID} for the whole request and echoes it back
 * on the response header. The MDC entry is always removed afterwards so no
 * value leaks into the next request.
 *
 * <p>Registered as a regular {@code @Component} filter bean (highest precedence)
 * so the request id is available to every downstream filter and handler.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Header used to propagate the correlation id. */
    public static final String CORRELATION_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(CORRELATION_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(CORRELATION_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.REQUEST_ID);
        }
    }
}
