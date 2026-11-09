package app.global.openapi;

import app.LibraryApplication;
import app.audit.controller.AuditController;
import app.audit.service.AuditService;
import app.info.controller.BuildInfoController;
import app.info.service.BuildInfoService;
import app.auth.controller.AuthController;
import app.auth.service.AuthService;
import app.book.controller.BookController;
import app.book.service.BookService;
import app.loan.controller.LoanController;
import app.loan.service.LoanService;
import app.user.controller.UserController;
import app.user.service.UserService;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
// API docs are off by default (application.properties); this test checks the document served when they are on.
@TestPropertySource(properties = "libro.api-docs.enabled=true")
@WebMvcTest({AuthController.class, BookController.class, UserController.class, LoanController.class,
        AuditController.class, BuildInfoController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({OpenApiConfig.class, OpenApiMvcTest.MockBeans.class})
@ContextConfiguration(classes = LibraryApplication.class)
@ImportAutoConfiguration({SpringDocConfigProperties.class, SpringDocConfiguration.class,
        SpringDocWebMvcConfiguration.class})
class OpenApiMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        BookService bookService() {
            return mock(BookService.class);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        /** Supplies the loan controller's business dependency without starting persistence. */
        @Bean
        LoanService loanService() {
            return mock(LoanService.class);
        }

        @Bean AuditService auditService() { return mock(AuditService.class); }
        @Bean BuildInfoService buildInfoService() { return mock(BuildInfoService.class); }
    }

    /** Verifies public schemas, security metadata, endpoint contracts, and loan pagination documentation. */
    @Test
    void generatedSpecDescribesRoutesAndProtectsPasswordFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isEmpty())
                .andExpect(jsonPath("$.paths['/app/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/app/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/app/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/app/auth/login'].post.responses['429']").exists())
                // Protected operations inherit the global bearerAuth requirement asserted above, so none is repeated per operation.
                .andExpect(jsonPath("$.paths['/app/books/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/app/books/{id}'].get.tags[0]").value("Books"))
                .andExpect(jsonPath("$.paths['/app/books/query'].get.parameters[?(@.name == 'minReplacementCost')]").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['200'].content['*/*'].schema").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400'].description")
                        .value("Invalid decimal, replacement-cost range, pagination, or sort field"))
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/app/books/replacement-cost'].get").exists())
                .andExpect(jsonPath("$.paths['/app/books/price']").doesNotExist())
                .andExpect(jsonPath("$.paths['/app/books/stats/average-replacement-cost'].get").exists())
                .andExpect(jsonPath("$.components.schemas.BookRequestDTO.properties.description.maxLength").value(2000))
                .andExpect(jsonPath("$.components.schemas.BookRequestDTO.properties.replacementCost").exists())
                .andExpect(jsonPath("$.components.schemas.BookRequestDTO.properties.price").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BookPatchRequestDTO.properties.description.maxLength").value(2000))
                .andExpect(jsonPath("$.paths['/app/users/{universityId}'].get.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/app/users/me'].get.description")
                        .value(Matchers.containsString("All four roles")))
                .andExpect(jsonPath("$.paths['/app/users/me'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/app/users/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/app/loans/me'].get.description")
                        .value(Matchers.containsString("Page numbers are zero-based")))
                .andExpect(jsonPath("$.paths['/app/loans/me'].get.description")
                        .value(Matchers.containsString("Allowed sort fields are id, borrowedAt, dueAt, returnedAt, and status")))
                .andExpect(jsonPath("$.paths['/app/loans'].get.description")
                        .value(Matchers.containsString("Library assistants see student and faculty loans")))
                .andExpect(jsonPath("$.paths['/app/loans'].get.parameters[?(@.name == 'status')]").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.parameters[?(@.name == 'overdue')]").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.parameters[?(@.name == 'borrower')]").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/app/loans'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['429']").exists())
                .andExpect(jsonPath("$.components.schemas.UserCreateRequestDTO.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.LoginRequestDTO.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.PasswordChangeRequestDTO.properties.currentPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.PasswordChangeRequestDTO.properties.newPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponseDTO.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponseDTO.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.fieldErrors").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.BookResponseDTO.properties.available").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.parameters[?(@.name == 'available')]").exists())
                .andExpect(jsonPath("$.paths['/app/users'].get.parameters[?(@.name == 'q')]").exists())
                .andExpect(jsonPath("$.paths['/app/loans/activity'].get").exists())
                .andExpect(jsonPath("$.paths['/app/loans/top-titles'].get").exists())
                .andExpect(jsonPath("$.paths['/app/info'].get").exists())
                .andExpect(jsonPath("$.paths['/app/audit'].get").exists());
    }

    /** Checks generated client metadata for the gaps found in the browser review. */
    @Test
    void generatedSpecHasSuccessSchemasBearerFailuresAndCoherentExamples() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var root = new ObjectMapper().readTree(body);
        var paths = root.path("paths");
        for (String[] route : new String[][] {
                {"/app/users", "get"}, {"/app/users/{universityId}", "put"},
                {"/app/books/{id}", "get"}, {"/app/books/all", "get"},
                {"/app/loans/me", "get"}, {"/app/loans/users/{universityId}", "get"}
        }) {
            var operation = paths.path(route[0]).path(route[1]);
            assertFalse(operation.path("responses").path("200").path("content").isMissingNode(), route[0]);
            assertTrue(operation.path("responses").path("200").path("content").elements().next()
                    .has("schema"), route[0]);
            assertTrue(operation.path("responses").has("401"), route[0]);
        }
        var sizeParameters = paths.path("/app/books/all").path("get").path("parameters");
        boolean capped = false;
        for (var parameter : sizeParameters) {
            if ("size".equals(parameter.path("name").asText())) {
                capped = parameter.path("schema").path("maximum").asInt() == 100;
            }
        }
        assertTrue(capped);
        assertTrue(body.contains("illustrative-only"));
        assertTrue(body.contains("ExampleOnly1!"));
        assertFalse(body.contains("Replacement cost must be greater than 0"));
        assertEquals(401, paths.path("/app/books/{id}").path("get").path("responses")
                .path("401").path("content").elements().next().path("example")
                .path("statusCode").asInt());
        assertEquals(0, paths.path("/app/books/all").path("get").path("responses")
                .path("200").path("content").elements().next().path("example")
                .path("data").path("totalElements").asInt());
    }
}
