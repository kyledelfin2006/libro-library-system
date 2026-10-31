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
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@OpenAPIDefinition(
        security = @SecurityRequirement(name = "bearerAuth"),
        info = @Info(
                title = "Libro Library API",
                version = "1.0.0",
                description = "Book collection, user account, and loan API for the Libro library management system prototype. "
                        + "POST /app/users/signup and POST /app/auth/login are public; all other routes require a 15-minute JWT Bearer access token issued by this API. "
                        + "Signup creates student patron accounts only. Only the library librarian creates school faculty and library assistant accounts. All roles may read books; library assistants and the librarian may add, update, or delete books. Library assistants may view and manage student/faculty accounts; the librarian may also manage library assistant accounts. Students and faculty may delete their own accounts. The API is stateless and accepts credentials only in the Authorization header.",
                license = @License(name = "Project-specific prototype; no distribution license declared")
        )
)
public class OpenApiConfig {
}
