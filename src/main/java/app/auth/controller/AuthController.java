package app.auth.controller;

import app.auth.dto.LoginRequestDTO;
import app.auth.dto.TokenResponseDTO;
import app.auth.service.AuthService;
import app.global.openapi.ErrorApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public HTTP adapter for authenticating Libro accounts. */
@RestController
@RequestMapping("/app/auth")
@Tag(name = "Authentication", description = "Issue short-lived access tokens for Libro API requests")
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the authentication controller.
     *
     * @param authService service that verifies credentials and issues access tokens
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates credentials and returns a non-cacheable Bearer token response.
     *
     * @param request university ID and password supplied by the client
     * @return the signed token and its lifetime
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and receive an access token",
            description = "Authenticates a university ID and password. Invalid credentials receive the same generic 401 response. The access token expires within 15 minutes and is returned with no-store caching.")
    @ApiResponse(responseCode = "200", description = "Credentials accepted; a short-lived Bearer token is returned")
    @ErrorApiResponse(responseCode = "400", description = "Login request is missing a valid university ID or password")
    @ErrorApiResponse(responseCode = "401", description = "University ID or password is incorrect")
    @ErrorApiResponse(responseCode = "429", description = "This client has sent too many login requests")
    public ResponseEntity<app.global.response.ApiResponse<TokenResponseDTO>> login(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
                    value = "{\"universityId\":\"2025-4321\",\"password\":\"illustrative-only\"}")))
            LoginRequestDTO request) {
        // Never cache a response containing a bearer credential.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(app.global.response.ApiResponse.ok(authService.login(request)));
    }
}
