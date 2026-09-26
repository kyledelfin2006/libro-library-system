package app.global.responses;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    // Getters
    private final boolean success;
    private String message;
    private T data; // actual data of the request
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