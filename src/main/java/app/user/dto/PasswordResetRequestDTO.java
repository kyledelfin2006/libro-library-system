package app.user.dto;

import app.user.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Temporary password supplied by authorized staff during account recovery. */
public record PasswordResetRequestDTO(
        @NotBlank
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE)
        @Schema(description = "Temporary password meeting the normal complexity policy and 72-byte BCrypt limit.",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        String temporaryPassword
) {
}
