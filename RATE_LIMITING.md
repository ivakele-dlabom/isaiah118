# Rate limiting the TakeMyBible backend (Spring Boot)

A learning guide with ready-to-paste code. Each step says which file to edit, shows the code, and explains what it does. File names are from your real project (`backend/backend/...`).

## 1. What rate limiting is and why you need it

Rate limiting caps how many requests one client can make in a period of time (for example, 5 login attempts per minute). When a client goes over the cap, the server answers **HTTP 429 Too Many Requests** instead of doing the work.

It protects you from:

- **Password guessing** on `/api/auth/login` (brute force), and mass account creation on `/api/auth/signup`.
- **Spam** on endpoints that write data: comments, new cross references, votes.
- **Scraping and accidental overload** on the public read endpoints (`/api/kjv/**`, `/api/bible/**`), which hit your database.
- **Cost:** on AWS a flood of requests means more CPU, more database load, and a bigger bill.

It does not replace authentication. It limits how fast someone can try.

## 2. Your project today

- Spring Boot with `spring-boot-starter-web` and `spring-boot-starter-security`.
- `security/SecurityConfig.java` builds one `SecurityFilterChain`. It allows `/api/auth/**` and GET requests to `/api/kjv/**`, `/api/bible/**` without a login. Everything else needs a JWT.
- `security/JwtAuthFilter.java` extends `OncePerRequestFilter`. It reads the JWT and stores an `AuthenticatedUser(id, email)` in the security context.
- In production, requests arrive through **nginx** (`frontend/nginx.conf`), which forwards `/api/` to the backend. The backend sees nginx's address unless you handle that (Section 6).

There is no rate limiting anywhere yet.

## 3. Where can you rate limit?

| Layer | Tool | Pros | Cons |
|---|---|---|---|
| Edge / proxy | nginx `limit_req` | Very cheap. Rejected requests never reach Java. | Only knows IP and URL. Not user-aware. |
| Cloud | AWS WAF rate-based rules | Blocks before your server. | Extra cost and setup. |
| Application | A Spring filter | Knows the user and the endpoint. Full control. | Uses your server's CPU and memory. |

**For learning Spring, do the application layer.** Then add nginx as a coarse safety net (Section 10).

## 4. The core idea: the token bucket

- Each client has a **bucket** that holds up to N tokens (the *capacity*).
- Every request removes **one token**. If the bucket is empty, the request is rejected with 429.
- Tokens are **refilled** at a steady rate.
- A full bucket allows a short **burst**, then the client is held to the steady rate.

Example: capacity 5, refill 5 tokens per minute. A user can try 5 logins immediately, then about one more every 12 seconds.

Other algorithms: **fixed window** (count per clock minute; allows a double burst at the boundary), **sliding window** (smoother, more memory), **leaky bucket** (constant processing rate). Token bucket is a good default.

## 5. Add the dependencies

Edit `backend/backend/pom.xml` and add these two inside `<dependencies>`:

```xml
<!-- Rate limiting: token bucket algorithm (plain Java library, no Spring wrapper) -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>REPLACE_WITH_LATEST</version>
</dependency>

<!-- Cache with automatic expiry, used to store one bucket per client.
     Spring Boot manages its version, so no <version> tag. -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Replace `REPLACE_WITH_LATEST` with the newest release from [Maven Central](https://central.sonatype.com/artifact/com.bucket4j/bucket4j_jdk17-core) (the Bucket4j README lists the same coordinates). I used the plain core library because your project is on Spring Boot 4.1.0, and a library with no Spring dependency is far less likely to break on a new Spring version.

Check that it downloads:

```powershell
cd backend\backend
.\mvnw.cmd -q dependency:resolve
```

## 6. Decide who "a client" is, and fix the nginx IP problem

A rate limit needs a **key**. Each key gets its own bucket.

| Situation | Key |
|---|---|
| Not logged in (login, signup, public reads) | The client's **IP address** |
| Logged in (comments, votes, new cross references) | The **user id** from the JWT. This is fairer, because many users share one IP on a school or office network. |

### The problem

Behind nginx, `request.getRemoteAddr()` returns the nginx container's address for **every** visitor. If you key on that, all users share one bucket and get blocked together.

`frontend/nginx.conf` already sends `X-Forwarded-For` with the real address. Make Tomcat (the web server inside Spring Boot) use it by adding **one line** to `application.properties`:

```properties
# Use X-Forwarded-For when the request comes from a trusted proxy (nginx).
# After this, request.getRemoteAddr() returns the real visitor's IP.
server.forward-headers-strategy=native
```

How it works: Tomcat only trusts the header when the request comes from an "internal proxy". By default that means private ranges (`10.x`, `172.16-31.x`, `192.168.x`), which includes Docker's network. A request from the public internet with a faked header is ignored, as long as the backend is not directly reachable from the internet. In your `docker-compose.yml` it is not, because only nginx publishes a port.

Check it works (Section 12). Never skip this: without it, one abusive user blocks everyone.

## 7. Choose limits and put them in configuration

Add to `application.properties`:

```properties
# Rate limits (requests per minute per client)
rate-limit.auth-per-minute=5
rate-limit.write-per-minute=30
rate-limit.read-per-minute=300
```

| Group | Requests | Key | Limit |
|---|---|---|---|
| AUTH | `POST /api/auth/login`, `POST /api/auth/signup` | IP | 5/min |
| WRITE | any non-GET request under `/api/` (comments, new cross reference, votes) | user id | 30/min |
| READ | any GET under `/api/` | IP | 300/min |

Why these numbers: a person rarely needs more than a few login attempts. Reading the Bible loads many chapters quickly, so reads must be generous. Open the browser dev tools (Network tab) and count how many API calls one page makes before you choose the read limit. Too strict a limit breaks normal use.

Because they are properties, you can override them without recompiling. In `docker-compose.yml`, under `backend: environment:`, you could add `RATE_LIMIT_AUTH_PER_MINUTE: "5"`. Spring maps `RATE_LIMIT_AUTH_PER_MINUTE` to `rate-limit.auth-per-minute` automatically.

## 8. Write the filter

Create `backend/backend/src/main/java/com/takemybible/backend/security/RateLimitFilter.java`:

```java
package com.takemybible.backend.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /** The three kinds of request we limit separately. */
    private enum Group { AUTH, WRITE, READ }

    private final int authPerMinute;
    private final int writePerMinute;
    private final int readPerMinute;

    /**
     * One bucket per client key (for example "AUTH:ip:1.2.3.4").
     * Caffeine removes a bucket after 10 minutes without use, so memory cannot
     * grow forever. This must be LONGER than the refill period (1 minute),
     * otherwise a client could reset its bucket by waiting.
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

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
        if (!path.startsWith("/api/")) return null;       // static files etc.

        if (path.equals("/api/auth/login") || path.equals("/api/auth/signup")) return Group.AUTH;
        if ("GET".equals(method)) return Group.READ;
        return Group.WRITE;
    }

    /** Logged-in writers are counted by user id, everyone else by IP. */
    private String clientKey(HttpServletRequest request, Group group) {
        if (group == Group.WRITE) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                return "user:" + user.id();
            }
        }
        // With server.forward-headers-strategy=native this is the real visitor IP.
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
```

What the important parts do:

- `OncePerRequestFilter` guarantees one run per request, the same as your `JwtAuthFilter`.
- `buckets.get(key, k -> newBucket(group))` returns the existing bucket or creates one atomically.
- `capacity(perMinute)` is the maximum burst. `refillGreedy(perMinute, Duration.ofMinutes(1))` adds tokens continuously, so 5 per minute means one token every 12 seconds.
- `tryConsumeAndReturnRemaining(1)` takes a token if one exists, and in either case tells you how long until the next one.
- A rejected request returns before `filterChain.doFilter`, so no controller code or database query runs.
- The `@Component` annotation makes Spring Boot also register the filter with the web server. This is harmless because `OncePerRequestFilter` skips a second run in the same request, and your `JwtAuthFilter` works the same way.

## 9. Register the filter in `SecurityConfig`

The filter needs the logged-in user, which `JwtAuthFilter` sets. So it must run **after** `JwtAuthFilter`.

Edit `security/SecurityConfig.java`.

Add a field and extend the constructor:

```java
private final JwtAuthFilter jwtAuthFilter;
private final RateLimitFilter rateLimitFilter;

public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.rateLimitFilter = rateLimitFilter;
}
```

In `filterChain`, add one `addFilterAfter` line:

```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(rateLimitFilter, JwtAuthFilter.class)        // <-- new line
.exceptionHandling(e -> e.authenticationEntryPoint(
        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
```

Order in the chain: CORS, then `JwtAuthFilter`, then `RateLimitFilter`, then Spring's authorization checks. CORS runs first, so a 429 still carries the CORS headers and the browser can read it.

One consequence: an unauthenticated flood still passes through the JWT filter before being rejected. That is cheap (it only looks at one header), so it is fine here.

## 10. Add a coarse limit in nginx (safety net)

nginx rejects floods before Java does any work. Edit `frontend/nginx.conf`.

At the **top of the file**, above `server {`:

```nginx
# One shared memory zone, keyed by client IP, allowing 10 requests per second.
limit_req_zone $binary_remote_addr zone=api_per_ip:10m rate=10r/s;
```

Inside the existing `location /api/ { ... }` block, add:

```nginx
limit_req zone=api_per_ip burst=40 nodelay;   # allow short bursts of 40 extra requests
limit_req_status 429;                         # same status code as the Spring filter
```

Keep this limit generous. The precise rules live in Spring. nginx is only there to stop floods.

If a load balancer sits in front of nginx on AWS, nginx would see the load balancer's IP for all visitors (the same problem as Section 6). In that case you must configure nginx's real IP module (`set_real_ip_from` and `real_ip_header X-Forwarded-For`) first. Test this on the server.

After changing `nginx.conf`, rebuild the web image: `docker compose up -d --build web`.

## 11. Things that are easy to get wrong

1. **All users share one bucket.** Caused by skipping the `server.forward-headers-strategy=native` line in Section 6.
2. **Memory growth.** Already handled by the Caffeine expiry and `maximumSize`. Do not replace it with a plain `HashMap`.
3. **Multiple backend instances.** Buckets live in one JVM's memory. With two backend containers each has its own count, so the real limit doubles. One EC2 server is fine. When you scale out, move the bucket state to Redis (Bucket4j has Redis integrations) or limit at nginx or AWS WAF.
4. **Restarts reset all counts.** Acceptable for a first version.
5. **Credential stuffing across IPs.** An attacker can rotate IPs and attack one account. Later, add a second limit keyed by the email being tried, inside `AuthController.login` (not the filter, because a request body can only be read once).
6. **Do not log passwords or tokens.** The filter above logs only the key and path.
7. **Frontend.** Make the React app show a friendly message on a 429 response and respect the `Retry-After` header.

## 12. How to test it

### Manual test with PowerShell

With the backend running (through Docker on port 80, or directly on its port), send 8 login attempts in a row. Replace the URL if needed:

```powershell
1..8 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost/api/auth/login" -Method Post `
             -ContentType "application/json" -Body '{"email":"a@b.com","password":"wrong"}' -UseBasicParsing
        "$_ -> $($r.StatusCode)"
    } catch {
        "$_ -> $([int]$_.Exception.Response.StatusCode)"
    }
}
```

Expected: the first 5 return `401` (wrong password; the exact status depends on your `AuthController`), and attempts 6 to 8 return `429`. Wait a minute and try again: it works again.

### Check the real IP is used

Start the app and watch the logs while sending a request through nginx:

```powershell
docker compose logs -f backend
```

Trigger the limit and look for the `Rate limit hit: key=AUTH:ip:...` line. The IP should be your real address, **not** a Docker address like `172.x.x.x`. If it is a `172.x` address, the forwarded-headers setting is not working.

### Check separation

Hit the limit from your PC, then call the login endpoint from another device (phone on mobile data). The second device should not be blocked.

### Check normal use

Browse the site as a normal user for several minutes, reading many chapters quickly. You should never see a 429. If you do, raise `rate-limit.read-per-minute`.

## 13. Suggested order of work

1. Add the dependencies (Section 5) and confirm Maven resolves them.
2. Add `server.forward-headers-strategy=native` and the three `rate-limit.*` properties.
3. Create `RateLimitFilter` and register it (Sections 8 and 9).
4. To test quickly, temporarily set `rate-limit.auth-per-minute=2` and run the PowerShell test.
5. Set sensible limits, then add the nginx safety net.
6. Update the frontend to handle 429.

## 14. Reading list

- Spring Security reference: servlet architecture and the filter chain.
- Spring Boot reference: "Running behind a front-end proxy server".
- [Bucket4j documentation](https://bucket4j.com/) and the [GitHub README](https://github.com/bucket4j/bucket4j).
- nginx documentation: `ngx_http_limit_req_module`.
- OWASP: "Blocking Brute Force Attacks" and the API Security Top 10 entry on unrestricted resource consumption.
- AWS WAF: rate-based rules (to block at the AWS edge later).
