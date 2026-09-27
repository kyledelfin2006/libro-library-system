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
@Schema(description = "Password-change request. Both values must be 8–72 characters and include uppercase and lowercase letters, a number, and a symbol.")
public class ChangePasswordDTO {

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    @Schema(description = "Current password, verified against the stored hash.", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String currentPassword;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    @Schema(description = "New password to store as a hash.", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String newPassword;

}
