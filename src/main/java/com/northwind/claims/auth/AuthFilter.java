package com.northwind.claims.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The API gateway authenticates users and forwards X-User-Id and X-User-Role, signed with
 * {@link GatewaySignature} in X-Gateway-Signature over X-Gateway-Timestamp (epoch seconds).
 * Requests without a valid, fresh signature are rejected, as are all requests when no secret is configured.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {
    public static final String SIGNATURE_HEADER = "X-Gateway-Signature";
    public static final String TIMESTAMP_HEADER = "X-Gateway-Timestamp";
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final String signingSecret;

    public AuthFilter(@Value("${northwind.gateway.signing-secret:}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String id = singleHeader(request, "X-User-Id");
        String role = singleHeader(request, "X-User-Role");
        if (id == null || id.isBlank() || role == null || !signedByGateway(request, id, role)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            request.setAttribute(CurrentUser.ATTRIBUTE, new CurrentUser(id, Role.valueOf(role)));
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean signedByGateway(HttpServletRequest request, String id, String role) {
        String signature = singleHeader(request, SIGNATURE_HEADER);
        String timestamp = singleHeader(request, TIMESTAMP_HEADER);
        if (signingSecret == null || signingSecret.isBlank() || signature == null || timestamp == null) {
            return false;
        }
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (RuntimeException e) {
            return false;
        }
        if (Duration.between(signedAt, Instant.now()).abs().compareTo(MAX_CLOCK_SKEW) > 0) {
            return false;
        }
        return GatewaySignature.matches(signingSecret, signature, id, role, timestamp, request.getMethod(),
                requestTarget(request));
    }

    /** Returns the header value, or null if it is missing or sent more than once. */
    private static String singleHeader(HttpServletRequest request, String name) {
        var values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }

    static String requestTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
