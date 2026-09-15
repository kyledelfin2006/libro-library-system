package app.user.dto;

import app.user.entity.enums.UserCourse;
import app.user.entity.enums.UserITMajor;
import app.user.entity.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private String universityId;

    private String firstName;

    private String lastName;

    private String middleInitial;

    private String email;

    private UserRole userRole;

    // Applies only to students
    private UserCourse userCourse;

    // Applies only to I.T students
    private UserITMajor major;
}
