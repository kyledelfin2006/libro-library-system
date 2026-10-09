package unit.global;

import app.LibraryApplication;
import app.book.controller.BookAPI;
import app.book.mapper.BookMapper;
import app.book.service.BookService;
import app.global.config.OpenApiConfig;
import app.user.controller.UserAPI;
import app.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BookAPI.class, UserAPI.class})
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
        BookService bookService() {
            return mock(BookService.class);
        }

        @Bean
        BookMapper bookMapper() {
            return mock(BookMapper.class);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }

    @Test
    void generatedSpecDescribesRoutesAndProtectsPasswordFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.security[0].basicAuth").isEmpty())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/app/books/{id}'].get.tags[0]").value("Books"))
                .andExpect(jsonPath("$.paths['/app/books/query'].get.parameters[?(@.name == 'minPrice')]").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.parameters[?(@.name == 'page')]").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['200'].content['*/*'].schema").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400'].description")
                        .value("Invalid decimal, price range, pagination, or sort field"))
                .andExpect(jsonPath("$.paths['/app/books/query'].get.responses['400'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/app/users/{universityId}'].get.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/app/users/signup'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.UserCreateRequestDTO.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordDTO.properties.currentPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordDTO.properties.newPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponseDTO.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponseDTO.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.fieldErrors").exists());
    }
}
