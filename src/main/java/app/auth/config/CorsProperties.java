package app.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Holds exact browser origins allowed to call the API; Spring's CORS filter handles preflights before authentication.
 * The optional {@code libro.security.cors.allowed-origins} property comes from
 * {@code LIBRO_CORS_ALLOWED_ORIGINS} and defaults to no origins.
 */
@ConfigurationProperties(prefix = "libro.security.cors")
public record CorsProperties(String allowedOrigins) {

    /**
     * Normalizes the comma-separated origin list and rejects wildcards at startup.
     *
     * @param allowedOrigins exact origins separated by commas, blank when no browser UI is allowed
     * @throws IllegalArgumentException if a wildcard origin is supplied
     */
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? "" : allowedOrigins.trim();
        if (Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException(
                    "LIBRO_CORS_ALLOWED_ORIGINS must contain exact origins; wildcard origins are not allowed");
        }
    }

    /**
     * Returns the configured exact origins for Spring's CORS allowlist.
     *
     * @return an immutable list, empty when the UI origin setting is blank
     */
    public List<String> origins() {
        if (allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }
}
