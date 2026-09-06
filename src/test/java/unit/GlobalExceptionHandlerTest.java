package unit;

import app.book.entity.Book;
import app.book.exceptions.BookNotFoundException;
import app.book.exceptions.BookValidationException;
import app.global.exceptions.GlobalExceptionHandler;
import app.global.responses.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for every public exception-mapping method in
 * {@link GlobalExceptionHandler}.
 *
 * <p>The handler has no injected collaborators, so each test calls it as a plain Java object
 * and inspects the returned {@link ResponseEntity}. This deliberately avoids a Spring MVC
 * context: the suite proves response construction, while controller-advice discovery and HTTP
 * serialization remain integration-test concerns.</p>
 *
 * <p>Every exception is a real framework object, keeping this dependency-free handler suite free
 * of mocking and instrumentation startup.</p>
 */
class GlobalExceptionHandlerTest {

    /** Stateless dependency-free handler shared by every isolated scenario. */
    private static final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    /** Verifies that invalid business arguments become a descriptive HTTP 400 response. */
    @Test
    void shouldReturnBadRequestWhenIllegalArgumentExceptionThrown() {
        IllegalArgumentException exception = new IllegalArgumentException("Price must be greater than 0");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed", "Price must be greater than 0");
    }

    /** Verifies that numeric parsing failures retain their message under the number-format title. */
    @Test
    void shouldReturnBadRequestWhenNumberFormatExceptionThrown() {
        NumberFormatException exception = new NumberFormatException("For input string: invalid");

        ResponseEntity<ErrorResponse> response = handler.handleNumberFormat(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Invalid Number Format", "For input string: invalid");
    }

    /**
     * Verifies malformed JSON maps to a stable HTTP 400 message without exposing parser details.
     */
    @Test
    void shouldReturnBadRequestWhenHttpMessageIsNotReadable() {
        MockHttpInputMessage input = new MockHttpInputMessage("invalid-json".getBytes(StandardCharsets.UTF_8));
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("Internal parser details", input);

        ResponseEntity<ErrorResponse> response = handler.handleJsonParseError(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Processing Error", "Invalid JSON format in request body");
        assert response.getBody() != null;
        assertThat(response.getBody().getDetails()).doesNotContain("Internal parser details");
    }

    /**
     * Verifies that all Jakarta validation messages are combined in binding-result order.
     *
     * @throws NoSuchMethodException if the local validation-target fixture cannot be reflected
     */
    @Test
    void shouldReturnBadRequestWithCombinedMessagesWhenMethodArgumentValidationFails() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "bookRequest");
        bindingResult.addError(new FieldError("bookRequest", "title", "Title cannot be empty"));
        bindingResult.addError(new FieldError("bookRequest", "price", "Price must be greater than 0"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationFailures(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed",
                "Title cannot be empty, Price must be greater than 0");
    }

    /** Verifies book business validation has its own HTTP 400 handler. */
    @Test
    void shouldReturnBadRequestWhenBookValidationExceptionThrown() {
        BookValidationException exception = new BookValidationException("Price must be greater than 0");

        ResponseEntity<ErrorResponse> response = handler.handleBookValidation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed", "Price must be greater than 0");
    }

    /** Verifies service-layer entity violations use the standard validation response contract. */
    @Test
    void shouldReturnBadRequestWhenEntityValidationFails() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Book> violation = org.mockito.Mockito.mock(ConstraintViolation.class);
        org.mockito.Mockito.when(violation.getMessage()).thenReturn("Title cannot be blank");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed", "Title cannot be blank");
    }

    /**
     * Verifies that an unmapped route reports both the attempted HTTP method and URL.
     *
     */
    @Test
    void shouldReturnNotFoundWhenNoHandlerExistsForEndpoint() {
        NoHandlerFoundException exception = new NoHandlerFoundException("GET", "/invalid-url", null);

        ResponseEntity<ErrorResponse> response = handler.handleEndpointNotFound(exception);

        assertError(response, HttpStatus.NOT_FOUND, "Endpoint not found",
                "No handler found for GET /invalid-url");
    }

    /** Verifies HTTP 405 mapping and disclosure of the supported method alternatives. */
    @Test
    void shouldReturnMethodNotAllowedWhenHttpMethodIsUnsupported() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "DELETE"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Method not allowed");
        assertThat(response.getBody().getDetails())
                .contains("POST is not supported for this endpoint")
                .contains("GET")
                .contains("DELETE");
        assertThat(response.getBody().getStatusCode()).isEqualTo(405);
    }

    /**
     * Verifies generic HTTP 500 database messaging and prevents SQL/internal details from leaking.
     */
    @Test
    void shouldReturnGenericInternalServerErrorWhenDataAccessFails() {
        DataAccessException exception = new DataAccessException("SELECT secret FROM credentials") { };

        ResponseEntity<ErrorResponse> response = handler.handleDataAccessError(exception);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "DataAccess error",
                "An unexpected error occurred while trying to access the database");
        assert response.getBody() != null;
        assertThat(response.getBody().getDetails())
                .doesNotContain("SELECT", "secret", "credentials");
    }

    /**
     * Verifies constraint failures map to HTTP 409 without revealing constraint names or SQL text.
     */
    @Test
    void shouldReturnConflictWhenDataIntegrityConstraintIsViolated() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate key value violates unique constraint books_title_key");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(exception);

        assertError(response, HttpStatus.CONFLICT, "Data integrity violation",
                "The operation would violate a database constraint");
        assert response.getBody() != null;
        assertThat(response.getBody().getDetails()).doesNotContain("books_title_key", "duplicate key");
    }

    /** Verifies that the domain-specific missing-book exception maps to HTTP 404. */
    @Test
    void shouldReturnNotFoundWhenBookDoesNotExist() {
        BookNotFoundException exception = new BookNotFoundException("Couldn't find book of ID: 99");

        ResponseEntity<ErrorResponse> response = handler.handleBookNotFound(exception);

        assertError(response, HttpStatus.NOT_FOUND, "Book not found", "Couldn't find book of ID: 99");
    }

    /**
     * Verifies invalid Spring Data property references map to HTTP 400.
     *
     * <p>The exception is mocked because its real constructor requires Spring Data type-path
     * metadata irrelevant to the handler's observable contract.</p>
     */
    @Test
    void shouldReturnBadRequestWhenPropertyReferenceIsInvalid() {
        PropertyReferenceException exception = new PropertyReferenceException(
                "unknownField", TypeInformation.of(Book.class), List.of());

        ResponseEntity<ErrorResponse> response = handler.handlePropertyReference(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Property reference error");
        assertThat(response.getBody().getDetails()).contains("unknownField");
        assertThat(response.getBody().getStatusCode()).isEqualTo(400);
    }

    /**
     * Verifies that query/path type mismatches identify both the rejected value and parameter.
     *
     * <p>A local method parameter supplies the minimal real Spring metadata required by the
     * exception constructor.</p>
     */
    @Test
    void shouldReturnBadRequestWhenMethodArgumentTypeDoesNotMatch() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class), 0);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "not-a-number", Number.class, "maxPrice", parameter, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Invalid value 'not-a-number' for parameter 'maxPrice'");
    }

    /** Verifies the catch-all response hides the original exception's implementation detail. */
    @Test
    void shouldReturnGenericInternalServerErrorForUnhandledException() {
        Exception exception = new Exception("Sensitive implementation detail");

        ResponseEntity<ErrorResponse> response = handler.handleEverythingElse(exception);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Internal server malfunctioned.");
        assert response.getBody() != null;
        assertThat(response.getBody().getDetails()).doesNotContain("Sensitive implementation detail");
    }

    /**
     * Reflection fixture used to construct a realistic {@link MethodParameter} for
     * {@link MethodArgumentNotValidException}.
     *
     * @param request unused placeholder representing a controller request argument
     */
    @SuppressWarnings("unused")
    private void validationTarget(Object request) {
        // Used only to construct a realistic MethodParameter for validation testing.
    }

    /**
     * Applies assertions shared by standard {@link ErrorResponse} mappings.
     *
     * @param response response returned by the handler method under test
     * @param expectedStatus expected HTTP and embedded numeric status
     * @param expectedError expected public error category
     * @param expectedDetails expected public detail message
     */
    private void assertError(ResponseEntity<ErrorResponse> response, HttpStatus expectedStatus,
                             String expectedError, String expectedDetails) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo(expectedError);
        assertThat(response.getBody().getDetails()).isEqualTo(expectedDetails);
        assertThat(response.getBody().getStatusCode()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().getTimestamp()).isPositive();
    }
}
