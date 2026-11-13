package app.auth;

import app.LibraryApplication;
import app.auth.config.JwtConfiguration;
import app.auth.config.PasswordConfig;
import app.auth.ratelimit.PublicRouteRateLimitConfig;
import app.auth.config.SecurityConfig;
import app.auth.controller.AuthController;
import app.auth.service.AuthService;
import app.book.controller.BookController;
import app.global.exception.GlobalExceptionHandler;
import app.user.controller.UserController;
import app.auth.service.LibroUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A management port equal to the application port is not private, so the Actuator paths must stay protected. */
@ActiveProfiles("test")
@WebMvcTest({AuthController.class, BookController.class, UserController.class})
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AuthService.class,
        LibroUserDetailsService.class,
        PasswordConfig.class,
        PublicRouteRateLimitConfig.class,
        GlobalExceptionHandler.class,
        SecurityFlowMvcTest.MockBeans.class,
        SecurityFlowMvcTest.HealthProbeController.class
})
@ContextConfiguration(classes = LibraryApplication.class)
@TestPropertySource(properties = {"server.port=9091", "management.server.port=9091"})
class SharedManagementPortMvcTest {

    @Autowired
    private MockMvc mockMvc;

    /** Even a request on the shared port is rejected without credentials, not treated as private management traffic. */
    @Test
    void sharedPortKeepsActuatorProtected() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(request -> { request.setLocalPort(9091); return request; }))
                .andExpect(status().isUnauthorized());
    }
}
