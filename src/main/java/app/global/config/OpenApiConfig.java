package app.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Global metadata for the generated Libro OpenAPI document. */
@Configuration
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic"
)
@OpenAPIDefinition(
        security = @SecurityRequirement(name = "basicAuth"),
        info = @Info(
                title = "Libro Library API",
                version = "1.0.0",
                description = "Book collection and user account API for the Libro library management system prototype. "
                        + "Book and user routes are live; loan routes are not implemented. HTTP Basic authentication uses a university ID and password; POST /app/users/signup is public, and all other routes require authentication. "
                        + "Signup is public and creates students only. Administrators create faculty. Students may read books; faculty and administrators may manage books; only administrators may create faculty or delete student/faculty accounts. CSRF protection remains enabled, so state-changing requests also need a valid CSRF token.",
                license = @License(name = "Project-specific prototype; no distribution license declared")
        )
)
public class OpenApiConfig {
}
