package app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete profile replacement. First name, last name, and email are required. The university ID, role, course, major, and password are unchanged.")
public class UserReplaceRequest {


    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Schema(example = "Alex")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Schema(example = "Rivera")
    private String lastName;

    @Size(max = 1, message = "Middle initial must be one character")
    @Schema(description = "Optional middle initial; blank input is stored as absent.", example = "M")
    private String middleInitial;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(example = "alex.rivera@example.edu")
    private String email;

    
}
