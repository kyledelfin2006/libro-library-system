package app.info.controller;

import app.global.response.ApiResponse;
import app.info.dto.BuildInfoDTO;
import app.info.service.BuildInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff-only release identity endpoint. */
@RestController
@Tag(name = "Information", description = "Safe release information for library staff")
public class BuildInfoController {
    private final BuildInfoService service;

    public BuildInfoController(BuildInfoService service) {
        this.service = service;
    }

    /** Returns the version, build time, short commit, and environment label. */
    @GetMapping("/app/info")
    @Operation(summary = "Get running build information")
    public ResponseEntity<ApiResponse<BuildInfoDTO>> info() {
        return ResponseEntity.ok(ApiResponse.ok(service.current()));
    }
}
