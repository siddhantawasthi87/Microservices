package com.concepts.requestfilter;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Autowired
    private Tracer tracer;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String uniqueID = tracer.currentSpan().context().traceId();
            if(uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
            }
            MDC.put(CORRELATION_ID, uniqueID);
            response.setHeader(CORRELATION_HEADER, uniqueID);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }

    }
}
