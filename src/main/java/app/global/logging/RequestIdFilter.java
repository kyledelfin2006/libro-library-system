package app.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns every request a correlation ID so a UI report can be traced to server log lines.
 * <p>
 * The ID is stored in the SLF4J MDC under {@link #MDC_KEY}, echoed in the {@code X-Request-Id} response header, and
 * copied into error bodies. It runs before Spring Security so 401/403 and 429 responses carry an ID too.
 * An inbound {@code X-Request-Id} is trusted only if it is short and made of safe characters; anything else is
 * replaced with a generated UUID, so a caller can never inject newlines or markup into log output.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Response and request header carrying the correlation ID. */
    public static final String HEADER = "X-Request-Id";
    /** MDC key referenced by the logging pattern and by {@code ErrorResponse}. */
    public static final String MDC_KEY = "requestId";

    /** Letters, digits and hyphens, at most 64 characters: enough for a UUID, too restrictive for log injection. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /**
     * Sets the request ID for the duration of the request and always clears it afterwards.
     *
     * @param request incoming request
     * @param response outgoing response
     * @param chain remaining filters
     * @throws ServletException if a later filter fails
     * @throws IOException if a later filter fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String requestId = inbound != null && SAFE_ID.matcher(inbound).matches()
                ? inbound
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        // Set the header first so it is present even if a later filter commits an error response.
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled; a leftover ID would leak into the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }
}
