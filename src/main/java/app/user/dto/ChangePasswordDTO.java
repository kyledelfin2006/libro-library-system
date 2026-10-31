package app.user.dto;

import app.user.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Password-change request. The new password must be 8–72 characters and UTF-8 bytes and include uppercase and lowercase letters, a number, and a symbol. The current password is required for self-service changes and may be omitted for authorized staff resets.")
public class ChangePasswordDTO {

    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    @Schema(description = "Required for your own password change; may be omitted when authorized staff reset another account's password.", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String currentPassword;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    @Schema(description = "New password to store as a hash; maximum 72 UTF-8 bytes.", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String newPassword;

}
