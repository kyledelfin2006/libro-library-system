package app.global.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

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
                license = @License(name = "MIT", url = "https://opensource.org/licenses/MIT")
        )
)
public class OpenApiConfig {
    /** Adds the common Bearer failure and enforced page-size ceiling to generated protected operations. */
    @Bean
    OpenApiCustomizer commonContractCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperations().forEach(operation -> {
                    // Only login and signup are public application operations.
                    if (!path.equals("/app/auth/login") && !path.equals("/app/users/signup")) {
                        operation.getResponses().putIfAbsent("401", new ApiResponse()
                                .description("Bearer token is missing or invalid")
                                .content(new Content().addMediaType("application/json", new MediaType()
                                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
                    }
                    // Swagger otherwise synthesizes statusCode=0 for every shared error schema.
                    operation.getResponses().forEach((code, response) -> {
                        if (code.matches("[45][0-9]{2}") && response.getContent() != null) {
                            String category = switch (code) {
                                case "400" -> "Bad Request";
                                case "401" -> "Unauthorized";
                                case "403" -> "Forbidden";
                                case "404" -> "Not Found";
                                case "409" -> "Conflict";
                                case "429" -> "Too Many Requests";
                                default -> "Error";
                            };
                            response.getContent().values().forEach(media -> media.setExample(Map.of(
                                    "error", category, "details", response.getDescription(),
                                    "timestamp", 1720000000000L, "statusCode", Integer.parseInt(code),
                                    "fieldErrors", Map.of(), "requestId", "3f2b8c1e-5a41-4c0e-9a57-0d6f1b7e2c11")));
                        }
                    });
                    var success = operation.getResponses().get("200");
                    boolean paged = success != null && success.getContent() != null
                            && success.getContent().values().stream().anyMatch(media -> media.getSchema() != null
                            && String.valueOf(media.getSchema().get$ref()).contains("Page"));
                    if (paged) {
                        // Springdoc does not infer Pageable parameters for every controller signature.
                        if (operation.getParameters() == null) {
                            operation.setParameters(new java.util.ArrayList<>());
                        }
                        if (operation.getParameters().stream().noneMatch(parameter -> "page".equals(parameter.getName()))) {
                            operation.addParametersItem(new Parameter().name("page").in("query")
                                    .description("Zero-based page index; default 0.")
                                    .schema(new Schema<>().type("integer")._default(0)));
                        }
                        if (operation.getParameters().stream().noneMatch(parameter -> "size".equals(parameter.getName()))) {
                            operation.addParametersItem(new Parameter().name("size").in("query")
                                    .description("Page size; default 12, maximum 100.")
                                    .schema(new Schema<>().type("integer")._default(12)));
                        }
                        if (operation.getParameters().stream().noneMatch(parameter -> "sort".equals(parameter.getName()))) {
                            operation.addParametersItem(new Parameter().name("sort").in("query")
                                    .description("property,direction; allowed fields vary by operation.")
                                    .schema(new Schema<>().type("string")));
                        }
                        operation.getParameters().stream()
                                .filter(parameter -> "size".equals(parameter.getName()) && parameter.getSchema() != null)
                                .forEach(parameter -> parameter.getSchema().setMaximum(new java.math.BigDecimal(100)));
                        // A whole-response example avoids Swagger combining a sample row with zero totals.
                        if (success.getContent() != null) {
                            var page = Map.ofEntries(
                                    Map.entry("content", java.util.List.of()), Map.entry("totalElements", 0),
                                    Map.entry("totalPages", 0), Map.entry("size", 12), Map.entry("number", 0),
                                    Map.entry("first", true), Map.entry("last", true), Map.entry("empty", true),
                                    Map.entry("numberOfElements", 0),
                                    Map.entry("sort", Map.of("empty", true, "sorted", false, "unsorted", true)),
                                    Map.entry("pageable", Map.of("pageNumber", 0, "pageSize", 12, "offset", 0,
                                            "paged", true, "unpaged", false,
                                            "sort", Map.of("empty", true, "sorted", false, "unsorted", true))));
                            success.getContent().values().forEach(media ->
                                    media.setExample(Map.of("success", true, "message", "Success", "data", page,
                                            "timestamp", 1720000000000L)));
                        }
                    }
                }));
    }
}
