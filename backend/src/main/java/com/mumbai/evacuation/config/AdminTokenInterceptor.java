package com.mumbai.evacuation.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects state-changing operator endpoints (disasters, shelter capacity)
 * with a shared secret sent as the {@code X-Admin-Token} header.
 *
 * Live disasters and shelter occupancy are global state seen by every user,
 * so anonymous visitors must not be able to change them. When ADMIN_TOKEN is
 * not configured (local development) the endpoints stay open and a warning is
 * logged at startup — always set it in any deployed environment.
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";
    private static final Logger log = LoggerFactory.getLogger(AdminTokenInterceptor.class);

    private final byte[] token;

    public AdminTokenInterceptor(@Value("${security.admin-token:}") String token) {
        this.token = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
        if (this.token == null) {
            log.warn("ADMIN_TOKEN is not set — disaster and shelter management endpoints are OPEN to everyone. "
                    + "Set ADMIN_TOKEN in any deployed environment.");
        }
    }

    public boolean isRequired() {
        return token != null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (token == null || HttpMethod.GET.matches(request.getMethod()) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String supplied = request.getHeader(HEADER);
        if (supplied != null && MessageDigest.isEqual(token, supplied.trim().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Operator token required for this action\"}");
        return false;
    }
}
