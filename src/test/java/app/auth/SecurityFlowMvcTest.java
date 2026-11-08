package app.auth;

import app.LibraryApplication;
import app.auth.service.LibroUserDetailsService;
import app.auth.config.SecurityConfig;
import app.auth.config.CorsProperties;
import app.auth.config.JwtConfiguration;
import app.auth.config.JwtProperties;
import app.auth.ratelimit.PublicRouteRateLimitConfig;
import app.auth.controller.AuthController;
import app.auth.service.AuthService;
import app.book.controller.BookController;
import app.book.dto.BookResponseDTO;
import app.book.service.BookService;
import app.global.exception.GlobalExceptionHandler;
import app.auth.config.PasswordConfig;
import app.user.controller.UserController;
import app.user.dto.UserResponseDTO;
import app.user.entity.User;
import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.repository.UserRepository;
import app.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.jayway.jsonpath.JsonPath;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MVC tests for real login, JWT validation, route roles, and account lookup. */
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
@TestPropertySource(properties = {"libro.security.cors.allowed-origins=https://ui.example.edu", "libro.api-docs.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityFlowMvcTest {

    private static final String UNIVERSITY_ID = "2025-4321";
    private static final String PASSWORD = "GoodPassword1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private BookService bookService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    private String passwordHash;

    @TestConfiguration(proxyBeanMethods = false)
    static class MockBeans {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        BookService bookService() {
            return mock(BookService.class);
        }
    }

    /** Provides the Actuator health response shape inside this MVC slice so production security rules are exercised. */
    @org.springframework.web.bind.annotation.RestController
    static class HealthProbeController {

        /** Returns the minimal liveness payload used by the Actuator endpoint. */
        @org.springframework.web.bind.annotation.GetMapping("/livez")
        java.util.Map<String, String> liveness() {
            return java.util.Map.of("status", "UP");
        }

        /** Returns the minimal readiness payload used by the Actuator endpoint. */
        @org.springframework.web.bind.annotation.GetMapping("/readyz")
        java.util.Map<String, String> readiness() {
            return java.util.Map.of("status", "UP");
        }
    }

    @org.junit.jupiter.api.BeforeAll
    void encodePasswordOnce() {
        passwordHash = passwordEncoder.encode(PASSWORD);
    }

    @BeforeEach
    void resetMocks() {
        reset(userRepository, userService, bookService);
    }

    /** Confirms signup remains public and the user service, not the body, selects the account role. */
    @Test
    void signupIsAccessibleWithoutBearerToken() throws Exception {
        when(userService.createUser(any())).thenReturn(userResponse());

        mockMvc.perform(post("/app/users/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "universityId":"2025-4321",
                                  "password":"GoodPassword1!",
                                  "firstName":"Alex",
                                  "lastName":"Rivera",
                                  "email":"alex.rivera@example.edu",
                                  "userRole":"STUDENT",
                                  "userCourse":"IT",
                                  "major":"SE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.universityId").value(UNIVERSITY_ID));

        verify(userService).createUser(any());
    }

    /** Confirms protected routes reject an absent token before invoking feature code. */
    @Test
    void protectedBookEndpointRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/app/books/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookService);
    }

    /** Confirms filter-chain 401 responses use the standard JSON error body and Bearer challenge. */
    @Test
    void anonymousRequestReceivesStandardJsonUnauthorizedBody() throws Exception {
        mockMvc.perform(get("/app/books/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    /** The own-profile route requires Bearer authentication and uses the signed subject. */
    @Test
    void myProfileRequiresTokenAndUsesItsSubject() throws Exception {
        mockMvc.perform(get("/app/users/me"))
                .andExpect(status().isUnauthorized());

        when(userService.getUserByUniversityId(
                org.mockito.ArgumentMatchers.eq(UNIVERSITY_ID),
                org.mockito.ArgumentMatchers.eq(UNIVERSITY_ID),
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(userResponse());
        mockMvc.perform(get("/app/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.FACULTY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(UNIVERSITY_ID));
    }

    /** Confirms the OpenAPI/Swagger routes are not rejected by the security chain when docs are enabled (default). */
    @Test
    void apiDocsRoutesArePermittedWhenEnabled() throws Exception {
        // This slice has no springdoc controller, so a permitted route reaches MVC and returns 404, not 401.
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    /** Confirms liveness and readiness probes are public and return no component details. */
    @Test
    void healthProbesArePublicAndHideDetails() throws Exception {
        for (String path : List.of("/livez", "/readyz")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    /** Confirms no other Actuator path is public through the health-probe matcher. */
    @Test
    void otherActuatorPathsRemainProtected() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    /** Confirms the allowlisted UI can complete its Authorization-header preflight without a JWT. */
    @Test
    void allowedUiOriginCanPreflightProtectedRoutes() throws Exception {
        mockMvc.perform(options("/app/books/1")
                        .header("Origin", "https://ui.example.edu")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "https://ui.example.edu"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Expose-Headers", "Retry-After"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Access-Control-Allow-Credentials"));
    }

    /** Confirms a browser origin outside the configured UI allowlist is rejected. */
    @Test
    void disallowedUiOriginCannotPreflightProtectedRoutes() throws Exception {
        mockMvc.perform(options("/app/books/1")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** Confirms empty configuration allows no origins and wildcard configuration fails during binding. */
    @Test
    void corsPropertiesRequireExactOriginsAndDefaultToNone() {
        assertTrue(new CorsProperties("").origins().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new CorsProperties("https://*.example.edu"));
    }

    /** Exercises credential authentication, token issuance, and real Bearer validation end to end. */
    @Test
    void validLoginIssuesTokenThatAuthenticatesProtectedRoute() throws Exception {
        User user = libroUser();
        when(userRepository.findByUniversityId(UNIVERSITY_ID)).thenReturn(Optional.of(user));
        when(bookService.findBookById(1L)).thenReturn(bookResponse());

        var login = mockMvc.perform(post("/app/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"2025-4321","password":"GoodPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userRole").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Pragma", "no-cache"))
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        assertNull(login.getRequest().getSession(false), "stateless login must not create an HTTP session");
        var decoded = jwtDecoder.decode(token);
        assertEquals(UNIVERSITY_ID, decoded.getSubject());
        assertEquals(List.of("STUDENT"), decoded.getClaimAsStringList("roles"));
        assertEquals(900, Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).getSeconds());
        assertEquals(Set.of("iss", "sub", "aud", "roles", "iat", "exp"),
                decoded.getClaims().keySet());
        assertFalse(decoded.getClaims().containsKey("password"));
        assertFalse(decoded.getClaims().containsKey("passwordHash"));
        assertFalse(decoded.getClaims().containsKey("email"));
        mockMvc.perform(get("/app/books/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        verify(userRepository, times(2)).findByUniversityId(UNIVERSITY_ID);
        verify(bookService).findBookById(1L);
    }

    /** Login reads the stored recovery flag and returns it outside the signed token. */
    @Test
    void loginReportsRequiredPasswordChange() throws Exception {
        User user = libroUser();
        user.setMustChangePassword(true);
        when(userRepository.findByUniversityId(UNIVERSITY_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/app/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"universityId\":\"2025-4321\",\"password\":\"GoodPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    /** Confirms unknown accounts and incorrect passwords share one client-visible failure. */
    @Test
    void invalidLoginUsesGenericResponseForUnknownIdAndWrongPassword() throws Exception {
        when(userRepository.findByUniversityId(UNIVERSITY_ID)).thenReturn(Optional.of(libroUser()));
        when(userRepository.findByUniversityId("2025-1111")).thenReturn(Optional.empty());

        String wrongPassword = mockMvc.perform(post("/app/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"2025-4321","password":"WrongPassword1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownId = mockMvc.perform(post("/app/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"2025-1111","password":"WrongPassword1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals((String) JsonPath.read(wrongPassword, "$.error"), (String) JsonPath.read(unknownId, "$.error"));
        assertEquals((String) JsonPath.read(wrongPassword, "$.details"), (String) JsonPath.read(unknownId, "$.details"));
        assertEquals("University ID or password is incorrect", JsonPath.read(wrongPassword, "$.details"));
        verifyNoInteractions(bookService);
    }

    /** Confirms bean validation rejects malformed credentials before account lookup. */
    @Test
    void malformedLoginIsRejectedBeforeAccountLookup() throws Exception {
        mockMvc.perform(post("/app/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"not-an-id","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));

        verifyNoInteractions(userRepository);
    }

    /** Confirms an over-72-byte UTF-8 password receives the same generic login failure. */
    @Test
    void multibytePasswordOverBcryptByteLimitUsesGenericLoginFailure() throws Exception {
        String password = "Ä".repeat(37);

        mockMvc.perform(post("/app/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"universityId\":\"2025-4321\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.details").value("University ID or password is incorrect"));

        verifyNoInteractions(userRepository);
    }

    /** Confirms Basic credentials cannot authenticate after the Bearer-only cutover. */
    @Test
    void basicCredentialsNoLongerAuthenticateProtectedRoutes() throws Exception {
        mockMvc.perform(get("/app/books/1")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthorization(PASSWORD)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository, bookService);
    }

    /** Rejects malformed compact tokens, altered signatures, and the unsigned algorithm. */
    @Test
    void malformedAlteredSignatureAndUnsignedTokensAreRejected() throws Exception {
        assertRejected("not-a-compact-jwt");

        String valid = validToken();
        String[] segments = valid.split("\\.");
        char first = segments[2].charAt(0);
        segments[2] = (first == 'A' ? "B" : "A") + segments[2].substring(1);
        assertRejected(String.join(".", segments));

        String unsigned = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + ".";
        assertRejected(unsigned);
    }

    /** Confirms the runtime accepts only its configured key and the HS256 algorithm. */
    @Test
    void wrongSecretAndUnacceptedAlgorithmAreRejected() throws Exception {
        JwtClaimsSet claims = validClaims();
        JwtEncoder wrongKeyEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec("different-secret-material-32-bytes".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();
        String wrongKey = wrongKeyEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertRejected(wrongKey);

        JwtEncoder hs384Encoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(new byte[64], "HmacSHA384"))
                .algorithm(MacAlgorithm.HS384)
                .build();
        String hs384 = hs384Encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS384).build(), claims)).getTokenValue();
        assertRejected(hs384);
    }

    /** Confirms issuer, single-audience, and not-before validation run before authorization. */
    @Test
    void wrongIssuerAudienceAndNotYetValidTokensAreRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(signedToken("different-issuer", List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of("other-api"),
                UNIVERSITY_ID, List.of("STUDENT"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(),
                List.of(jwtProperties.audience(), "other-api"), UNIVERSITY_ID,
                List.of("STUDENT"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now, now.plusSeconds(900), now.plusSeconds(31)));
    }

    /** Rejects absent, malformed, future, and overlong identity/time/role claims. */
    @Test
    void missingInvalidAndOverlongClaimsAreRejected() throws Exception {
        Instant now = Instant.now();
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                "", List.of("STUDENT"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, null, now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("UNKNOWN"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT", "LIBRARIAN"), now, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now, now.plusSeconds(901), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now, null, null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), null, now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now.plusSeconds(120), now.plusSeconds(900), null));
        assertRejected(signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), now.minusSeconds(931), now.minusSeconds(31), null));
        verifyNoInteractions(userRepository, bookService);
    }

    /** Confirms the default Bearer resolver ignores query, cookie, and form token transports. */
    @Test
    void accessTokenIsAcceptedOnlyFromAuthorizationHeader() throws Exception {
        String token = validToken();
        mockMvc.perform(get("/app/books/1").queryParam("access_token", token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/app/books/1").header(HttpHeaders.COOKIE, "access_token=" + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/app/books/add")
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .param("access_token", token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository, bookService);
    }

    /** Confirms a signed librarian role reaches the existing account-creation route. */
    @Test
    void librarianCanCreateLibraryAssistant() throws Exception {
        when(userService.createAdministrator(any(), any())).thenReturn(new UserResponseDTO(
                "2025-9999", "Library", "Assistant", null,
                "assistant@example.edu", UserRole.ADMINISTRATOR, null, null, false));

        mockMvc.perform(post("/app/users/administrators")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.LIBRARIAN))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"universityId":"2025-9999","password":"GoodPassword1!",
                                 "firstName":"Library","lastName":"Assistant",
                                 "email":"assistant@example.edu","userRole":"LIBRARIAN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userRole").value("ADMINISTRATOR"));

        verify(userService).createAdministrator(any(), any());
    }

    /** Confirms a valid administrator token cannot create a library-assistant account. */
    @Test
    void administratorCannotCreateLibraryAssistant() throws Exception {
        mockMvc.perform(post("/app/users/administrators")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.ADMINISTRATOR))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    /** Only signed staff tokens reach the reset controller; the service checks the target role. */
    @Test
    void passwordResetRouteRequiresStaffToken() throws Exception {
        String path = "/app/users/2025-9999/password/reset";
        String body = "{\"temporaryPassword\":\"Temporary1!\"}";
        mockMvc.perform(put(path).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (UserRole patron : List.of(UserRole.STUDENT, UserRole.FACULTY)) {
            mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, bearerAuthorization(patron))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        for (UserRole staff : List.of(UserRole.ADMINISTRATOR, UserRole.LIBRARIAN)) {
            mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, bearerAuthorization(staff))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
        verify(userService, times(2)).resetPassword(any(), any(), any(), any());
    }

    /** Confirms the librarian token claim maps to the route's ROLE_LIBRARIAN authority. */
    @Test
    void librarianCanAddBooks() throws Exception {
        when(bookService.addBook(any())).thenReturn(bookResponse());

        mockMvc.perform(post("/app/books/add")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.LIBRARIAN))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"1984\",\"author\":\"George Orwell\",\"genre\":\"Dystopian\",\"replacementCost\":19.99}"))
                .andExpect(status().isCreated());

        verify(bookService).addBook(any());
    }

    /** Confirms library assistants retain the existing book-write permission. */
    @Test
    void administratorCanAddBooks() throws Exception {
        when(bookService.addBook(any())).thenReturn(bookResponse());

        mockMvc.perform(post("/app/books/add")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.ADMINISTRATOR))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"1984\",\"author\":\"George Orwell\",\"genre\":\"Dystopian\",\"replacementCost\":19.99}"))
                .andExpect(status().isCreated());

        verify(bookService).addBook(any());
    }

    /** Confirms a valid patron token is rejected by the staff-only book-write gate. */
    @Test
    void studentCannotAddBooks() throws Exception {
        mockMvc.perform(post("/app/books/add")
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.STUDENT))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"1984\",\"author\":\"George Orwell\",\"genre\":\"Dystopian\",\"replacementCost\":19.99}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookService);
    }

    /** Confirms the token subject is passed through as the owner for account deletion. */
    @Test
    void facultyCanReachOwnAccountDeletionRoute() throws Exception {
        mockMvc.perform(delete("/app/users/{universityId}", UNIVERSITY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerAuthorization(UserRole.FACULTY)))
                .andExpect(status().isOk());

        verify(userService).deleteUserByUniversityId(
                org.mockito.ArgumentMatchers.eq(UNIVERSITY_ID),
                org.mockito.ArgumentMatchers.eq(UNIVERSITY_ID),
                org.mockito.ArgumentMatchers.anyCollection());
    }

    private User libroUser() {
        return libroUser(UserRole.STUDENT);
    }

    private User libroUser(UserRole role) {
        User user = new User();
        user.setUniversityId(UNIVERSITY_ID);
        user.setPasswordHash(passwordHash);
        user.setUserRole(role);
        return user;
    }

    private UserResponseDTO userResponse() {
        return new UserResponseDTO(UNIVERSITY_ID, "Alex", "Rivera", null,
                "alex.rivera@example.edu", UserRole.STUDENT, UserCourse.IT, UserITMajor.SE, false);
    }

    private BookResponseDTO bookResponse() {
        return new BookResponseDTO(1L, "1984", "George Orwell", "Dystopian", new BigDecimal("19.99"));
    }

    /** Signs a short-lived test token with one role using the same encoder as the application. */
    private String bearerAuthorization(UserRole role) {
        Instant issuedAt = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(jwtProperties.issuer())
                        .subject(UNIVERSITY_ID)
                        .audience(List.of(jwtProperties.audience()))
                        .claim("roles", List.of(role.name()))
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(jwtProperties.accessTokenTtlSeconds()))
                        .build())).getTokenValue();
        return "Bearer " + token;
    }

    /** Builds an ordinary valid student token for negative-validation scenarios. */
    private String validToken() {
        return signedToken(jwtProperties.issuer(), List.of(jwtProperties.audience()),
                UNIVERSITY_ID, List.of("STUDENT"), Instant.now(), Instant.now().plusSeconds(900), null);
    }

    /** Creates reusable claims for tests that vary only the signing key or algorithm. */
    private JwtClaimsSet validClaims() {
        Instant issuedAt = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(UNIVERSITY_ID)
                .audience(List.of(jwtProperties.audience()))
                .claim("roles", List.of("STUDENT"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }

    /** Signs customized claims so each negative test exercises the real Bearer decoder. */
    private String signedToken(String issuer, List<String> audience, String subject,
                               List<?> roles, Instant issuedAt, Instant expiresAt, Instant notBefore) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        if (issuer != null) claims.issuer(issuer);
        if (audience != null) claims.audience(audience);
        if (subject != null) claims.subject(subject);
        if (roles != null) claims.claim("roles", roles);
        if (issuedAt != null) claims.issuedAt(issuedAt);
        if (expiresAt != null) claims.expiresAt(expiresAt);
        if (notBefore != null) claims.notBefore(notBefore);
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    /** Asserts invalid credentials stop at the Resource Server filter before book code runs. */
    private void assertRejected(String token) throws Exception {
        mockMvc.perform(get("/app/books/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /** Encodes a Basic header solely to prove the removed mechanism cannot authenticate. */
    private String basicAuthorization(String password) {
        String encoded = java.util.Base64.getEncoder().encodeToString(
                (UNIVERSITY_ID + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
