package app.global.exceptions;

import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.global.responses.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 * <p>
 * Intercepts exceptions thrown across the application and converts them into
 * standardized {@link ErrorResponse} JSON objects with appropriate HTTP status codes.
 * </p>
 * <p>
 * Lombok's {@code @Slf4j} supplies the class-wide logger used for internal diagnostics.
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Builds the backwards-compatible validation response used when no field map is available.
     *
     * @param message the client-safe validation summary
     * @return a validation error response with an empty {@code fieldErrors} map
     */
    private ErrorResponse buildValidationErrorResponse(String message) {
        return new ErrorResponse("Validation failed", message, 400);
    }

    /**
     * Builds a validation response containing both the legacy summary and structured field errors.
     *
     * @param message the combined client-safe validation summary
     * @param fieldErrors field or property-path names mapped to their messages
     * @return a structured HTTP 400 validation payload
     */
    private ErrorResponse buildValidationErrorResponse(String message, Map<String, String> fieldErrors) {
        return new ErrorResponse("Validation failed", message, 400, fieldErrors);
    }

    /**
     * Converts Spring MVC binding errors into a deterministic field-to-message map.
     *
     * Field errors use their request field name. Object-level errors use {@code _global}.
     * When multiple constraints target one field, their messages are joined rather than lost.
     * A {@link LinkedHashMap} preserves binding order in the generated response.
     *
     * @param bindingResult Spring's validation result for a request body
     * @return ordered field names mapped to client-safe validation messages
     */
    private Map<String, String> toFieldErrors(BindingResult bindingResult) {
        // Convert each Spring error to a public field key and message, merging duplicate keys.
        return bindingResult.getAllErrors().stream()
                .collect(Collectors.toMap(
                        error -> error instanceof FieldError fieldError ? fieldError.getField() : "_global",
                        error -> error.getDefaultMessage() == null ? "Validation failed" : error.getDefaultMessage(),
                        (first, next) -> first + "; " + next,
                        LinkedHashMap::new
                ));
    }

    /**
     * Converts Jakarta constraint violations into a deterministic field-to-message map.
     *
     * Property paths are sorted before collection so responses are stable across runs.
     * Violations without a property path are represented by {@code _global}; multiple
     * violations for one path are combined into one readable value.
     *
     * @param exception the service-layer constraint violation exception
     * @return ordered property paths mapped to client-safe validation messages
     */
    private Map<String, String> toFieldErrors(ConstraintViolationException exception) {
        // Sort property paths first so the fieldErrors JSON has predictable ordering.
        return exception.getConstraintViolations().stream()
                .sorted((first, second) -> String.valueOf(first.getPropertyPath())
                        .compareTo(String.valueOf(second.getPropertyPath())))
                .collect(Collectors.toMap(
                        violation -> {
                            // A missing path is a global error rather than a field error.
                            String propertyPath = violation.getPropertyPath() == null
                                    ? ""
                                    : violation.getPropertyPath().toString();
                            return propertyPath.isBlank()
                                    ? "_global"
                                    : propertyPath;
                        },
                        // Keep a safe fallback when a provider supplies no message.
                        violation -> violation.getMessage() == null
                                ? "Validation failed"
                                : violation.getMessage(),
                        // Preserve every message when several constraints share one field.
                        (first, next) -> first + "; " + next,
                        LinkedHashMap::new
                ));
    }

    /**
     * Handles {@link IllegalArgumentException} for invalid input arguments.
     *
     * @param ex the exception containing the invalid argument details
     * @return HTTP 400 Bad Request with a validation error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(ex.getMessage()));
    }

    /** Handles validation failures specific to book write operations. */
    @ExceptionHandler(BookValidationException.class)
    public ResponseEntity<ErrorResponse> handleBookValidation(BookValidationException ex) {
        log.warn("Book validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(ex.getMessage()));
    }

    /**
     * Handles validation failures for {@code @Valid} annotated request bodies.
     * <p>
     * Aggregates validation messages while also preserving a field-to-message map.
     * </p>
     *
     * @param ex the exception containing the binding results
     * @return HTTP 400 Bad Request with structured validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailures(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = toFieldErrors(ex.getBindingResult());
        String allErrors = String.join(", ", fieldErrors.values());
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(allErrors, fieldErrors));
    }

    /**
     * Handles entity or service-layer constraint violations.
     *
     * @param ex the exception containing Jakarta constraint violations
     * @return HTTP 400 with both summary and structured field-level errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = toFieldErrors(ex);
        String allErrors = String.join(", ", fieldErrors.values());
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(allErrors, fieldErrors));
    }


    /**
     * Handles {@link NumberFormatException} for invalid numeric arguments.
     *
     * @param ex the exception containing the invalid argument details
     * @return HTTP 400 Bad Request with a validation error response
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleNumberFormat(NumberFormatException ex) {
        log.warn("Number format error: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("Invalid Number Format", ex.getMessage(), 400);
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles malformed JSON in request bodies.
     *
     * @param ex the exception thrown when JSON cannot be parsed
     * @return HTTP 400 Bad Request with a clear JSON format error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseError(HttpMessageNotReadableException ex) {
        ErrorResponse error = new ErrorResponse("Processing Error", "Invalid JSON format in request body", 400);
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles requests to undefined endpoints.
     * <p>
     * Requires {@code spring.mvc.throw-exception-if-no-handler-found=true} and
     * {@code spring.web.resources.add-mappings=false} to be enabled.
     * </p>
     *
     * @param ex the exception containing the requested URL and HTTP method
     * @return HTTP 404 Not Found with the requested endpoint details
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleEndpointNotFound(NoHandlerFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                "Endpoint not found",
                "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                404
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles unsupported HTTP methods for a given endpoint.
     *
     * @param ex the exception containing the unsupported method and supported alternatives
     * @return HTTP 405 Method Not Allowed with the list of supported methods
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse error = new ErrorResponse(
                "Method not allowed",
                ex.getMethod() + " is not supported for this endpoint. Supported methods: " + ex.getSupportedHttpMethods(),
                405
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    /**
     * Handles generic data access errors (e.g., SQL exceptions, connection issues).
     * <p>
     * Returns a generic error message to the client to avoid exposing internal database details.
     * </p>
     *
     * @param ex the data access exception
     * @return HTTP 500 Internal Server Error with a generic database error message
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessError(DataAccessException ex) {
        log.error("Data Access error occurred", ex);
        ErrorResponse error = new ErrorResponse("DataAccess error",
                "An unexpected error occurred while trying to access the database", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles data integrity violations (e.g., foreign key or unique constraint breaches).
     * <p>
     * Returns a generic message to prevent leaking sensitive database structure information.
     * </p>
     *
     * @param ex the integrity violation exception
     * @return HTTP 409 Conflict indicating the operation violates a database constraint
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        ErrorResponse error = new ErrorResponse("Data integrity violation",
                "The operation would violate a database constraint", 409);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handles cases where a requested book does not exist in the database.
     *
     * @param ex the custom exception containing the missing book identifier
     * @return HTTP 404 Not Found with the specific book not found message
     */
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookNotFound(BookNotFoundException ex) {
        log.warn("Book not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse("Book not found", ex.getMessage(), 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handles errors related to invalid property references in Spring Data queries.
     * <p>
     * Occurs when a query tries to reference a property that does not exist on the entity.
     * </p>
     *
     * @param ex the property reference exception
     * @return HTTP 400 Bad Request with the invalid property details
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReference(PropertyReferenceException ex) {
        log.error("Property reference error occurred", ex);
        ErrorResponse error = new ErrorResponse("Property reference error", ex.getMessage(), 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles mismatches between request parameter types and expected method parameters.
     *
     * @param ex the exception containing the invalid value and parameter name
     * @return HTTP 400 Bad Request indicating the invalid parameter value
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());
        ErrorResponse error = new ErrorResponse("Invalid parameter", message, 400);
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Fallback handler for any unhandled exception not caught by more specific handlers.
     * <p>
     * Logs the full stack trace internally for debugging and returns a generic message to the client.
     * </p>
     *
     * @param ex the unhandled exception
     * @return HTTP 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEverythingElse(Exception ex) {
        log.error("Unexpected error occurred", ex);
        ErrorResponse error = new ErrorResponse("Internal server error", "Internal server malfunctioned.", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
