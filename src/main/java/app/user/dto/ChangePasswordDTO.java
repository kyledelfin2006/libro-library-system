package app.user.dto;

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
            regexp = ("\\d{4}")
    )
    private String currentPassword;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = ("\\d{4}")
    )
    private String newPassword;

}
