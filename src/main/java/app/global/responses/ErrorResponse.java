package app.global.responses;

import lombok.Getter;

import java.util.Map;

@Getter
public class ErrorResponse {
    private final String error;
    private final String details;
    private final long timestamp;
    private final int statusCode;
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
