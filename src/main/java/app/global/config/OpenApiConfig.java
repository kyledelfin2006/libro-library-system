package app.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/** Global metadata for the generated Libro OpenAPI document. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Libro Library API",
                version = "1.0.0",
                description = "Book collection and user account API for the Libro library management system prototype. "
                        + "Book and user routes are live; loan routes are not implemented.",
                license = @License(name = "Project-specific prototype; no distribution license declared")
        )
)
public class OpenApiConfig {
}
