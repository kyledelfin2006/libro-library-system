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
                description = "Book collection API for the Libro library management system prototype. "
                        + "Book routes are live; user and loan routes are planned and are not exposed yet.",
                license = @License(name = "Project-specific prototype; no distribution license declared")
        )
)
public class OpenApiConfig {
}
