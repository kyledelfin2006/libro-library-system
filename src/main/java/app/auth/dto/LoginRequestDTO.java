package app.auth.dto;

import app.user.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Credentials submitted to request a Libro access token. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "University ID and password used to log in. The password is never returned.")
public class LoginRequestDTO {

    /** The university ID used as the account's login name. */
    @NotBlank(message = "University ID is required")
    @Pattern(regexp = "\\d{4}-\\d{4}", message = "University ID must follow the format 2025-4321")
    @Schema(description = "University ID in ####-#### format.", example = "2025-4321")
    private String universityId;

    /** The raw password, passed only to Spring Security and never logged or stored. */
    @NotBlank(message = "Password is required")
    @Size(max = PasswordPolicy.MAX_PASSWORD_LENGTH,
            message = "Password must not exceed 72 characters")
    @Schema(description = "Password for this account. Maximum 72 characters and 72 UTF-8 bytes.",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
}
