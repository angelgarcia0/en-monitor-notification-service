package com.energymonitor.notification.infrastructure;

import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers 503 in the shared {@link ApiError} shape when a route's permission could not be checked
 * because the security service did not answer. Placed before the exception translation of the
 * filter chain, which lets any exception other than an authentication or access failure through.
 */
class SecurityServiceUnavailableFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityServiceUnavailableFilter.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    SecurityServiceUnavailableFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (SecurityServiceUnavailableException unavailable) {
            LOG.warn("Permission of {} not checked: {}", request.getRequestURI(),
                    unavailable.getMessage());
            if (response.isCommitted()) {
                throw unavailable;
            }
            HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(Instant.now(clock), status.value(), status.getReasonPhrase(),
                            "This section is not available right now. Try again in a few minutes.",
                            request.getRequestURI()));
        }
    }
}
