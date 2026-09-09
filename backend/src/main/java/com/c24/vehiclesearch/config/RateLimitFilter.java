package com.c24.vehiclesearch.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-client rate limit on the search endpoint.
 *
 * A public URL with a paid model behind it is a wallet-drain vector, and the
 * cheapest possible attack is a loop over unique query strings — every one a
 * cache miss, every one a model call. The daily budget inside GeminiClient caps
 * total spend; this caps the rate at which one caller can consume it, so a
 * single script cannot exhaust the day's allowance before anyone else arrives.
 *
 * A fixed window in memory, not a distributed limiter: one instance, one
 * process, and a sliding-window implementation would be more machinery than the
 * threat justifies. It resets on restart, which is acceptable for the same
 * reason.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerWindow;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${search.rate-limit.requests-per-minute:60}") int maxPerWindow) {
        this.maxPerWindow = maxPerWindow;
    }

    private record Window(Instant startedAt, AtomicInteger count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health checks and the schema endpoint are cheap and are polled by the
        // platform; only the endpoint that can reach the model is limited.
        return !"/api/v1/search".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (exceeded(clientKey(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write("""
                    {"error":"Too many searches","detail":"Limit is %d per minute. Try again shortly."}"""
                    .formatted(maxPerWindow));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceeded(String key) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || Duration.between(existing.startedAt(), now).compareTo(WINDOW) >= 0)
                        ? new Window(now, new AtomicInteger())
                        : existing);

        // Bounded so a flood of distinct source addresses cannot grow the map
        // without limit; dropping entries only loses rate history, never data.
        if (windows.size() > 10_000) windows.clear();

        return window.count().incrementAndGet() > maxPerWindow;
    }

    /**
     * Behind Caddy every request arrives from the proxy, so the forwarded
     * address is the only thing that distinguishes callers. It is client-supplied
     * and therefore spoofable — acceptable for cost control, not for anything
     * security-bearing.
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
