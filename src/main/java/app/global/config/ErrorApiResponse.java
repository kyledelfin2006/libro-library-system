package app.global.config;

import app.global.responses.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Documents an expected error response using the API's shared error payload schema.
 * The status and description remain specific to the endpoint using this annotation.
 */
@Target({METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
@Repeatable(ErrorApiResponses.class)
@ApiResponse(content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public @interface ErrorApiResponse {

    /** HTTP status code represented by this error response. */
    @AliasFor(annotation = ApiResponse.class, attribute = "responseCode")
    String responseCode() default "default";

    /** Endpoint-specific explanation of when the error is returned. */
    @AliasFor(annotation = ApiResponse.class, attribute = "description")
    String description() default "";
}
