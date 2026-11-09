package app.info.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public, staff-visible release identification without host or dependency details. */
public record BuildInfoDTO(String version, String buildTime,
                           @Schema(description = "Short source commit or unknown locally") String commit,
                           String environment) {
}
