package app.global.exceptions;

import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.global.responses.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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

/**
 * Global exception handler for REST controllers.
 * <p>
 * Intercepts exceptions thrown across the application and converts them into
 * standardized {@link ErrorResponse} JSON objects with appropriate HTTP status codes.
 * </p>
 * <p>
 * Slfj used class-wide to implement Slfj logging automatically without object declaration via Lombok Annotation
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Helper: centralizes construction of validation error responses so every validation
    // handler — whether triggered by @Valid (MethodArgumentNotValidException) or by manual
    // service-layer checks (IllegalArgumentException) — shares a single, tested code path.
    private ErrorResponse buildValidationErrorResponse(String message) {
        return new ErrorResponse("Validation failed", message, 400);
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
     * Aggregates all constraint violation messages into a single comma-separated string.
     * </p>
     *
     * @param ex the exception containing the binding results
     * @return HTTP 400 Bad Request with the aggregated validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailures(MethodArgumentNotValidException ex) {
        String allErrors = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .reduce((msg1, msg2) -> msg1 + ", " + msg2)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(allErrors));
    }

    /** Handles validation failures raised while enforcing entity constraints in the service. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String allErrors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .sorted()
                .reduce((message1, message2) -> message1 + ", " + message2)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(buildValidationErrorResponse(allErrors));
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
