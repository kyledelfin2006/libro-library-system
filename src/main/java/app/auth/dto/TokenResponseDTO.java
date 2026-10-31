package app.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Short-lived access token returned after successful credential authentication. */
@Schema(description = "Short-lived access token. Keep the token secret and send it only in the Authorization header.")
public record TokenResponseDTO(
        @Schema(description = "Signed access token used in Bearer authorization.") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Token lifetime in seconds from issuance.", example = "900") long expiresIn
) {
}
