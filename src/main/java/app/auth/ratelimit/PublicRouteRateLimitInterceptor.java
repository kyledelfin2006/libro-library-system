package app.auth.ratelimit;

import app.global.responses.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Limits repeated requests to the public login and signup endpoints by client IP. */
@Slf4j
public class PublicRouteRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<ClientRoute, RequestWindow> windows = new LinkedHashMap<>();
    private long requestsSinceCleanup;

    /**
     * Creates an in-memory limiter for the two public routes.
     *
     * @param properties configured quotas and maximum tracked client count
     * @param objectMapper writes the standard API error body for rejected requests
     */
    public PublicRouteRateLimitInterceptor(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks the matching POST request before controller validation or credential work.
     *
     * @param request incoming servlet request
     * @param response outgoing servlet response
     * @param handler selected MVC handler
     * @return true when the request is within its quota
     * @throws IOException if the standard error response cannot be written
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String route = request.getServletPath();
        RateLimitProperties.Limit limit = switch (route) {
            case "/app/auth/login" -> properties.login();
            case "/app/users/signup" -> properties.signup();
            default -> null;
        };
        if (limit == null) {
            return true;
        }

        long now = System.currentTimeMillis() / 1000;
        long windowNumber = now / limit.windowSeconds();
        long retryAfter = acquire(route, request.getRemoteAddr(), limit, windowNumber, now);
        if (retryAfter == 0) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                "Too many requests", "Request limit exceeded. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS.value()));
        return false;
    }

    /**
     * Atomically consumes one slot for a route and remote address.
     *
     * @param route public route path
     * @param remoteAddress address reported by the servlet container
     * @param limit configured request count and window duration
     * @param windowNumber current fixed-window number
     * @param now current epoch second
     * @return zero when allowed, otherwise seconds until the next window
     */
    private synchronized long acquire(String route, String remoteAddress,
                                      RateLimitProperties.Limit limit, long windowNumber, long now) {
        // ponytail: bounded local state avoids a dependency; high address churn can evict active quotas, so use a shared gateway store at scale.
        if (++requestsSinceCleanup % 256 == 0) {
            removeExpiredWindows(now);
        }

        ClientRoute key = new ClientRoute(route, remoteAddress == null ? "unknown" : remoteAddress);
        RequestWindow window = windows.get(key);
        if (window == null || window.windowNumber != windowNumber) {
            if (window == null && windows.size() >= properties.maxTrackedClientRoutes()) {
                Iterator<ClientRoute> oldest = windows.keySet().iterator();
                if (oldest.hasNext()) {
                    windows.remove(oldest.next());
                }
            }
            window = new RequestWindow(windowNumber, 0, false, limit.windowSeconds());
            windows.put(key, window);
        }

        if (window.requestCount < limit.requests()) {
            window.requestCount++;
            return 0;
        }

        if (!window.reported) {
            log.warn("Rate limit reached for public route {}", route);
            window.reported = true;
        }
        return Math.max(1, (windowNumber + 1) * limit.windowSeconds() - now);
    }

    /** Removes old window records so inactive client addresses do not accumulate indefinitely. */
    private void removeExpiredWindows(long now) {
        Iterator<Map.Entry<ClientRoute, RequestWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            RequestWindow window = iterator.next().getValue();
            if (now / window.windowSeconds != window.windowNumber) {
                iterator.remove();
            }
        }
    }

    private record ClientRoute(String route, String remoteAddress) {
    }

    private static final class RequestWindow {
        private final long windowNumber;
        private final long windowSeconds;
        private int requestCount;
        private boolean reported;

        private RequestWindow(long windowNumber, int requestCount, boolean reported, long windowSeconds) {
            this.windowNumber = windowNumber;
            this.requestCount = requestCount;
            this.reported = reported;
            this.windowSeconds = windowSeconds;
        }
    }
}
