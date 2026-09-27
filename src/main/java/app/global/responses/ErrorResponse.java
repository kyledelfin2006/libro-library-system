package app.global.responses;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Getter
@Schema(description = "Standard error response. Validation failures include fieldErrors keyed by request or entity field; non-field errors use _global.")
public class ErrorResponse {
    @Schema(example = "Validation failed")
    private final String error;
    @Schema(description = "Client-safe explanation. Does not include database internals or stack traces.", example = "Price must be greater than 0")
    private final String details;
    @Schema(description = "Response creation time as Unix epoch milliseconds.", example = "1720000000000")
    private final long timestamp;
    @Schema(example = "400")
    private final int statusCode;
    @Schema(description = "Field name to validation message mapping; _global identifies object-level errors.", example = "{\"price\":\"Price must be greater than 0\"}")
    private final Map<String, String> fieldErrors;

    public ErrorResponse(String error, String details, int statusCode) {
        this(error, details, statusCode, Map.of());
    }

    public ErrorResponse(String error, String details, int statusCode, Map<String, String> fieldErrors) {
        this.error = error;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
        this.statusCode = statusCode;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

}
