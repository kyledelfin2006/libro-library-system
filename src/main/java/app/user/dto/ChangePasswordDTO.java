package app.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ChangePasswordDTO {

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = ("\\d{4}")
    )
    private String currentPassword;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = ("\\d{4}")
    )
    private String newPassword;

}
