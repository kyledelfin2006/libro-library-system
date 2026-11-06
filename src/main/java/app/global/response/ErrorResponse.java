package app.global.response;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Getter
@Schema(description = "Standard error response. Validation failures include fieldErrors keyed by request or entity field; non-field errors use _global.")
public class ErrorResponse {
    @Schema(example = "Validation failed")
    private final String error;
    @Schema(description = "Client-safe explanation. Does not include database internals or stack traces.", example = "Replacement cost must be greater than 0")
    private final String details;
    @Schema(description = "Response creation time as Unix epoch milliseconds.", example = "1720000000000")
    private final long timestamp;
    @Schema(example = "400")
    private final int statusCode;
    @Schema(description = "Field name to validation message mapping; _global identifies object-level errors.", example = "{\"replacementCost\":\"Replacement cost must be greater than 0\"}")
    private final Map<String, String> fieldErrors;
    @Schema(description = "Correlation ID, also returned in the X-Request-Id header. Quote it when reporting a problem.", example = "3f2b8c1e-5a41-4c0e-9a57-0d6f1b7e2c11")
    private final String requestId;

    public ErrorResponse(String error, String details, int statusCode) {
        this(error, details, statusCode, Map.of());
    }

    public ErrorResponse(String error, String details, int statusCode, Map<String, String> fieldErrors) {
        this.error = error;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
        this.statusCode = statusCode;
        this.fieldErrors = Map.copyOf(fieldErrors);
        // Read from the logging context set by RequestIdFilter; null outside an HTTP request (for example in unit tests).
        this.requestId = org.slf4j.MDC.get("requestId");
    }

}
