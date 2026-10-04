package com.mumbai.evacuation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-IP, fixed-window (1 minute) rate limiter for endpoints that call
 * paid or rate-limited third-party APIs (Gemini, TomTom, Photon/Nominatim) or
 * that are CPU-heavy (simulations). Protects the free-tier quotas from abuse.
 *
 * The client IP is taken from the first X-Forwarded-For entry when present
 * (Render / Vercel set it), otherwise from the socket address. This is a
 * best-effort guard for a single instance, not a distributed limiter.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_KEYS = 50_000;

    /** Path prefix -> max requests per minute per IP. */
    private static final Map<String, Integer> LIMITS = new LinkedHashMap<>();
    static {
        LIMITS.put("/api/chat", 10);
        LIMITS.put("/api/live-route", 30);
        LIMITS.put("/api/search", 60);
        LIMITS.put("/api/geocode", 60);
        LIMITS.put("/api/evacuation/simulate", 20);
        LIMITS.put("/api/evacuation/compare", 20);
        LIMITS.put("/api/benchmark", 6);
    }

    private final boolean enabled;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${ratelimit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    private static final class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || limitFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String prefix = limitFor(request.getRequestURI());
        int limit = LIMITS.get(prefix);
        String key = prefix + "|" + clientIp(request);
        long now = System.currentTimeMillis();

        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MS);
        }
        Window window = windows.compute(key, (k, w) -> w == null || now - w.start >= WINDOW_MS ? new Window(now) : w);
        boolean allowed;
        synchronized (window) {
            allowed = ++window.count <= limit;
        }
        if (!allowed) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - window.start)) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded, retry in "
                    + retryAfter + "s\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String limitFor(String uri) {
        for (String prefix : LIMITS.keySet()) {
            if (uri.startsWith(prefix)) return prefix;
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
