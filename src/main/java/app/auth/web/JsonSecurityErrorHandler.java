package app.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the API's standard error JSON for 401 and 403 responses produced by the security filter chain.
 * <p>
 * These failures occur before Spring MVC runs, so {@code GlobalExceptionHandler} never sees them. Without this
 * class the UI would receive an empty body for a missing/invalid token or an insufficient role. The body never
 * echoes token contents or the failure reason, which avoids leaking validation details to unauthenticated callers.
 * </p>
 */
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * Rejects a request with a missing, malformed, expired or otherwise invalid Bearer token.
     *
     * @param request the rejected request
     * @param response the response to populate
     * @param authException the authentication failure (deliberately not exposed to the client)
     * @throws IOException if the body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // RFC 6750 requires the Bearer challenge so clients know which scheme to use.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required or the token is invalid");
    }

    /**
     * Rejects an authenticated caller whose role does not satisfy the route gate.
     *
     * @param request the rejected request
     * @param response the response to populate
     * @param accessDeniedException the authorization failure (deliberately not exposed to the client)
     * @throws IOException if the body cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to access this resource");
    }

    /**
     * Writes a body with the same fields as {@code ErrorResponse}.
     * A fixed string is used because the message values are constants and need no JSON escaping.
     */
    private void write(HttpServletResponse response, HttpStatus status, String error, String details)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":\"%s\",\"details\":\"%s\",\"timestamp\":%d,\"statusCode\":%d,\"fieldErrors\":{},\"requestId\":\"%s\"}"
                        .formatted(error, details, System.currentTimeMillis(), status.value(),
                                // RequestIdFilter guarantees a safe [A-Za-z0-9-] value, so no JSON escaping is needed.
                                java.util.Objects.toString(org.slf4j.MDC.get("requestId"), "")));
    }
}
