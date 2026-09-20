package app.user.dto;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.validation.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCreateRequestDTO {

    @NotBlank(message = "University ID is required")
    @Pattern(
            regexp = "\\d{4}-\\d{4}",
            message = "University ID must follow the format 2025-4321"
    )
    private String universityId;

    @NotBlank(message = "Password cannot be null")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50)
    private String lastName;

    @Size(max = 1, message = "Middle initial must be one character")
    private String middleInitial;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotNull(message = "User role is required")
    private UserRole userRole;

    // Applies only to students
    private UserCourse userCourse;

    // Applies only to I.T students
    private UserITMajor major;

}
