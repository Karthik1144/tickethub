package com.tickethub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, in-memory rate limiting for the two abuse-prone endpoints
 * (login and hold). One instance only; a shared store would be needed when scaled out.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final TicketHubProperties props;

    public RateLimitFilter(TicketHubProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        Integer limit = limitFor(request);
        if (limit != null) {
            String key = request.getMethod() + ":" + request.getRequestURI() + ":" + clientIp(request);
            if (!allow(key, limit)) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"title":"Too many requests","status":429,"code":"RATE_LIMITED",\
                        "detail":"Slow down and try again in a minute"}""");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Integer limitFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        if (uri.endsWith("/api/v1/auth/login")) {
            return props.getRatelimit().getLoginPerMinute();
        }
        if (uri.startsWith("/api/v1/shows/") && uri.endsWith("/holds")) {
            return props.getRatelimit().getHoldPerMinute();
        }
        return null;
    }

    private boolean allow(String key, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || existing.minute() != minute)
                        ? new Window(minute, new AtomicInteger(0))
                        : existing);
        return window.count().incrementAndGet() <= limit;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
