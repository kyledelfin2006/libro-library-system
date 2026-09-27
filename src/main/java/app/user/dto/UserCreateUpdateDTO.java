package app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial profile update. Omitted or null fields remain unchanged; supplied names and email must not be blank. A blank middle initial is treated as omitted.")
public class UserCreateUpdateDTO {

    @Size(max = 50, message = "First name must not exceed 50 characters.")
    @Schema(description = "Optional replacement name; trimmed before storage.", example = "Alex")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters.")
    @Schema(description = "Optional replacement name; trimmed before storage.", example = "Rivera")
    private String lastName;


    @Pattern(
            regexp = "[A-Za-z]?",
            message = "Middle initial must be one letter only."
    )
    @Schema(description = "Optional one-letter middle initial. Blank input is ignored.", example = "M")
    private String middleInitial;

    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters.")
    @Schema(description = "Optional replacement email; trimmed and stored lowercase.", example = "alex.rivera@example.edu")
    private String email;
}
