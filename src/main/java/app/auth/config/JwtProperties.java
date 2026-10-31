package app.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment-controlled issuer, audience, signing key, and maximum access-token lifetime. */
@Validated
@ConfigurationProperties(prefix = "libro.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secretBase64,
        @Positive @Max(900) long accessTokenTtlSeconds
) {
}
