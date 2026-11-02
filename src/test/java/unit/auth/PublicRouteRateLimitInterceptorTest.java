package unit.auth;

import app.auth.ratelimit.PublicRouteRateLimitInterceptor;
import app.auth.ratelimit.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused checks for public-route quota enforcement and its HTTP response. */
class PublicRouteRateLimitInterceptorTest {

    private final PublicRouteRateLimitInterceptor interceptor = new PublicRouteRateLimitInterceptor(
            new RateLimitProperties(new RateLimitProperties.Limit(1, 60),
                    new RateLimitProperties.Limit(1, 3600), 10),
            new ObjectMapper());

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

    /** Creates a request with an explicit source address so the per-client key is deterministic. */
    private MockHttpServletRequest post(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
