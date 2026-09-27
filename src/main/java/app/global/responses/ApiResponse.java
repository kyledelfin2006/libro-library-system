package app.global.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "Success envelope used by selected API operations. Some read operations return their DTO directly.")
@Getter
public class ApiResponse<T> {
    // Getters
    @Schema(description = "Whether the operation completed successfully.", example = "true")
    private final boolean success;
    @Schema(description = "Human-readable operation result.", example = "Operation completed successfully")
    private String message;
    @Schema(description = "Operation result. The schema is specialized to each endpoint's response type.")
    private T data; // actual data of the request
    @Schema(description = "Response creation time as Unix epoch milliseconds.", example = "1720000000000")
    private final long timestamp; // Time Of API Response

    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, T data) {
        this.success = success;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

}
