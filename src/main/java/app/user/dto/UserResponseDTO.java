package app.user.dto;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public user profile. Passwords and password hashes are never included.")
public class UserResponseDTO {

    @Schema(description = "University identifier in ####-#### format.", example = "2025-4321")
    private String universityId;

    private String firstName;

    private String lastName;

    private String middleInitial;

    private String email;

    private UserRole userRole;

    @Schema(description = "Present for student accounts.")
    private UserCourse userCourse;

    @Schema(description = "Present only for IT student accounts.")
    private UserITMajor major;
}
