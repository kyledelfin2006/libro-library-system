package app.global.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Container for multiple endpoint-specific {@link ErrorApiResponse} annotations. */
@Target({METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
public @interface ErrorApiResponses {
    ErrorApiResponse[] value();
}
