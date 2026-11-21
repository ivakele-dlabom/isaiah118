
package com.takemybible.backend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private enum Group { AUTH, WRITE, READ }

    private final int authPerMinute;
    private final int writePerMinute;
    private final int readPerMinute;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10)) // Delete bucket after 10min of no use
            .maximumSize(100_000) // Bucket limit
            .build();

    public RateLimitFilter(
            @Value("${rate-limit.auth-per-minute:5}") int authPerMinute,
            @Value("${rate-limit.write-per-minute:30}") int writePerMinute,
            @Value("${rate-limit.read-per-minute:300}") int readPerMinute) {
        this.authPerMinute = authPerMinute;
        this.writePerMinute = writePerMinute;
        this.readPerMinute = readPerMinute;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        Group group = classify(request);
        if (group == null) {                       // not something we limit
            filterChain.doFilter(request, response);
            return;
        }

        String key = group + ":" + clientKey(request, group);
        Bucket bucket = buckets.get(key, k -> newBucket(group));

        // Try to take one token. The "probe" also tells us what is left.
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // if 1 token is consumed successfully
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);   // allowed: continue as normal
            return;
        }

        // Rejected. Tell the client how many seconds to wait (rounded up, at least 1).
        long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);

        logger.warn("Rate limit hit: key=" + key + " path=" + request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());       // 429
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Too many requests, try again later.\"}");
        // We do NOT call filterChain.doFilter, so the controller never runs.
    }

    /** Decide which group a request belongs to, or null to skip limiting. */
    private Group classify(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equals(method)) return null;        // browser CORS preflight: never count
        if (!path.startsWith("/api/")) return null;       // static files etc. is not a backend request

        if (path.equals("/api/auth/login") || path.equals("/api/auth/signup")) return Group.AUTH;
        if ("GET".equals(method)) return Group.READ;
        return Group.WRITE;
    }

    /** Logged-in writers are counted by user id, everyone else by IP. */
    private String clientKey(HttpServletRequest request, Group group) {
        if (group == Group.WRITE) {
            // This must be a logged in user since they gonna perform a write operation
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                return "user:" + user.id();
            }
        }
        // With server.forward-headers-strategy=native this is the real visitor IP.
        // must be a READ or AUTH operation
        return "ip:" + request.getRemoteAddr();
    }

    /** A new bucket: full at the start, refilled evenly over one minute. */
    private Bucket newBucket(Group group) {
        int perMinute = switch (group) {
            case AUTH -> authPerMinute;
            case WRITE -> writePerMinute;
            case READ -> readPerMinute;
        };
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
