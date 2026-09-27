package app.user.dto;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import app.user.validation.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "User account creation request. Students require a course and IT students require a major; faculty must leave course and major null.")
public class UserCreateRequestDTO {

    @NotBlank(message = "University ID is required")
    @Pattern(
            regexp = "\\d{4}-\\d{4}",
            message = "University ID must follow the format 2025-4321"
    )
    @Schema(description = "University ID in ####-#### format.", example = "2025-4321")
    private String universityId;

    @NotBlank(message = "Password cannot be null")
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.PASSWORD_REQUIREMENTS_MESSAGE
    )
    @Schema(description = "Password meeting the 8–72 character complexity policy. Never returned by the API.", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @NotBlank(message = "First name is required")
    @Size(max = 50)
    @Schema(example = "Alex")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50)
    @Schema(example = "Rivera")
    private String lastName;

    @Size(max = 1, message = "Middle initial must be one character")
    @Schema(description = "Optional middle initial.", example = "M")
    private String middleInitial;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(example = "alex.rivera@example.edu")
    private String email;

    @NotNull(message = "User role is required")
    @Schema(description = "Account role. Students require a course; faculty must leave course and major null.")
    private UserRole userRole;

    @Schema(description = "Required for students. Allowed values are IT, EMC, and IS.")
    private UserCourse userCourse;

    @Schema(description = "Required for IT students and omitted for all other users. Allowed values are SE, SMBPO, IST, and HN.")
    private UserITMajor major;

}
