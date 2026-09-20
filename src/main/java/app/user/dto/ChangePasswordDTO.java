package app.user.dto;

import app.user.validation.PasswordPolicy;
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
public class ChangePasswordDTO {

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    private String currentPassword;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    private String newPassword;

}
