package app.auth.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configurable request quotas for the public login and signup routes. */
@Validated
@ConfigurationProperties(prefix = "libro.security.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Limit login,
        @NotNull @Valid Limit signup,
        @Min(2) int maxTrackedClientRoutes
) {

    /** A fixed-window request quota. */
    public record Limit(
            @Min(1) int requests,
            @Min(1) @Max(86400) long windowSeconds
    ) {
    }
}
