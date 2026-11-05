package unit.auth;

import app.auth.ratelimit.PublicRouteRateLimitInterceptor;
import app.auth.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused checks for public-route quota enforcement and its HTTP response. */
class PublicRouteRateLimitInterceptorTest {

    private final PublicRouteRateLimitInterceptor interceptor = new PublicRouteRateLimitInterceptor(
            new RateLimitProperties(new RateLimitProperties.Limit(1, 60),
                    new RateLimitProperties.Limit(1, 3600), 10));

    /** Confirms a second login from the same address gets the documented retry response. */
    @Test
    void blocksLoginAfterConfiguredQuota() throws Exception {
        MockHttpServletRequest request = post("/app/auth/login", "192.0.2.10");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), this));

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, blocked, this));

        assertEquals(429, blocked.getStatus());
        assertTrue(Integer.parseInt(blocked.getHeader("Retry-After")) > 0);
        assertTrue(blocked.getContentAsString().contains("Request limit exceeded"));
    }

    /** Confirms quotas do not leak across routes or client addresses. */
    @Test
    void tracksRoutesAndClientAddressesSeparately() throws Exception {
        assertTrue(interceptor.preHandle(post("/app/auth/login", "192.0.2.10"),
                new MockHttpServletResponse(), this));
        assertTrue(interceptor.preHandle(post("/app/users/signup", "192.0.2.10"),
                new MockHttpServletResponse(), this));
        assertTrue(interceptor.preHandle(post("/app/auth/login", "192.0.2.11"),
                new MockHttpServletResponse(), this));
    }

    /** Confirms Spring's trusted forwarded-header processing supplies distinct client addresses to the limiter. */
    @Test
    void forwardedClientAddressesUseSeparateBucketsWhenProxyHeadersAreProcessed() throws Exception {
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postWithForwardedAddress("/app/auth/login", "192.0.2.30"), response,
                (request, ignored) -> assertTrue(interceptor.preHandle(
                        (HttpServletRequest) request, new MockHttpServletResponse(), this)));
        filter.doFilter(postWithForwardedAddress("/app/auth/login", "192.0.2.31"), response,
                (request, ignored) -> assertTrue(interceptor.preHandle(
                        (HttpServletRequest) request, new MockHttpServletResponse(), this)));
    }

    /** Confirms unprocessed X-Forwarded-For values cannot rotate the quota from one socket address. */
    @Test
    void forwardedAddressIsIgnoredWhenProxyHeadersAreNotProcessed() throws Exception {
        MockHttpServletRequest first = post("/app/auth/login", "10.0.0.8");
        first.addHeader("X-Forwarded-For", "192.0.2.40");
        MockHttpServletRequest second = post("/app/auth/login", "10.0.0.8");
        second.addHeader("X-Forwarded-For", "192.0.2.41");

        assertTrue(interceptor.preHandle(first, new MockHttpServletResponse(), this));
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(second, blocked, this));
        assertEquals(429, blocked.getStatus());
    }

    /** Creates a request with an explicit source address so the per-client key is deterministic. */
    private MockHttpServletRequest post(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    /** Creates a proxy request whose socket peer is trusted but whose forwarded client address varies. */
    private MockHttpServletRequest postWithForwardedAddress(String path, String forwardedAddress) {
        MockHttpServletRequest request = post(path, "10.0.0.5");
        request.addHeader("X-Forwarded-For", forwardedAddress);
        request.addHeader("X-Forwarded-Proto", "https");
        return request;
    }
}
