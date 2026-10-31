package app.auth.config;

import app.user.entity.enums.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Configures the Nimbus encoder, strict Libro token validation, and role conversion. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    private static final int MINIMUM_KEY_BYTES = 32;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /**
     * Decodes the environment-provided key and rejects malformed or undersized secret material at startup.
     *
     * @param properties configured Base64 signing key
     * @return the HMAC key shared by token issue and validation
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.secretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("LIBRO_JWT_SECRET_BASE64 must contain valid Base64", exception);
        }

        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("LIBRO_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * Creates a Nimbus encoder that always signs Libro tokens with HS256.
     *
     * @param key validated HMAC key
     * @return the HS256 token encoder
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Creates a decoder that verifies the signature and all required Libro claims.
     *
     * @param key validated HMAC key
     * @param properties trusted issuer, audience, and lifetime settings
     * @return the HS256 decoder used by Resource Server
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerAndTime = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(properties.issuer()));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().size() == 1
                && jwt.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : invalidToken();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerAndTime, audience, libroClaimsValidator(properties)));
        return decoder;
    }

    /**
     * Converts the single validated role name to the existing Spring ROLE_ authority.
     *
     * @return converter applied to each validated Bearer request
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    /**
     * Requires a non-empty subject, one supported role, and an issued lifetime within policy.
     *
     * @param properties configured maximum token lifetime
     * @return generic validation failure for invalid claims
     */
    private OAuth2TokenValidator<Jwt> libroClaimsValidator(JwtProperties properties) {
        return jwt -> {
            Object roleClaim = jwt.getClaims().get("roles");
            boolean oneKnownRole = roleClaim instanceof Collection<?> roles
                    && roles.size() == 1
                    && roles.iterator().next() instanceof String roleName
                    && Arrays.stream(UserRole.values()).anyMatch(role -> role.name().equals(roleName));
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            boolean validLifetime = issuedAt != null
                    && expiresAt != null
                    && expiresAt.isAfter(issuedAt)
                    && !expiresAt.isAfter(issuedAt.plusSeconds(properties.accessTokenTtlSeconds()))
                    && !issuedAt.isAfter(Instant.now().plus(CLOCK_SKEW));

            return jwt.getSubject() != null && !jwt.getSubject().isBlank() && oneKnownRole && validLifetime
                    ? OAuth2TokenValidatorResult.success()
                    : invalidToken();
        };
    }

    /** Returns a generic token failure without echoing sensitive claim values. */
    private static OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid access token", null));
    }
}
