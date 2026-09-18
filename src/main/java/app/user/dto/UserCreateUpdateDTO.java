package app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateUpdateDTO {

    @Size(max = 50, message = "First name must not exceed 50 characters.")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters.")
    private String lastName;


    @Pattern(
            regexp = "[A-Za-z]?",
            message = "Middle initial must be one letter only."
    )
    private String middleInitial;

    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters.")
    private String email;
}
