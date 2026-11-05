package unit.global;

import app.global.logging.RequestIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for correlation-ID assignment, propagation and log-injection protection. */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Runs the filter and returns the ID visible in the MDC while the chain executed. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    /** A well-formed inbound ID is reused in the MDC and echoed in the response header. */
    @Test
    void reusesValidInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals("abc-123", run(request, response));
        assertEquals("abc-123", response.getHeader(RequestIdFilter.HEADER));
    }

    /** A missing ID is generated, so every response carries one. */
    @Test
    void generatesIdWhenAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(new MockHttpServletRequest(), response);

        assertEquals(seen, response.getHeader(RequestIdFilter.HEADER));
        assertTrue(seen.matches("[0-9a-f-]{36}"));
    }

    /** Unsafe or oversized inbound IDs are replaced, never logged or echoed verbatim. */
    @Test
    void replacesMalformedInboundIds() throws Exception {
        for (String bad : new String[]{"bad id\nINJECTED", "<script>", "x".repeat(65), ""}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestIdFilter.HEADER, bad);
            MockHttpServletResponse response = new MockHttpServletResponse();

            String seen = run(request, response);

            assertNotEquals(bad, seen);
            assertTrue(seen.matches("[0-9a-f-]{36}"));
        }
    }

    /** The MDC is cleared after the request so pooled threads never leak an ID. */
    @Test
    void clearsMdcAfterRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
