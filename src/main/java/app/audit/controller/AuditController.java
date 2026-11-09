package app.audit.controller;

import app.audit.dto.AuditEventDTO;
import app.audit.service.AuditService;
import app.global.openapi.ErrorApiResponse;
import app.global.response.ApiResponse;
import app.user.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

/** Role-scoped staff read access to fixed-format change history. */
@RestController
@RequestMapping("/app/audit")
@Tag(name = "Audit", description = "Staff-visible change history")
public class AuditController {
    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** Returns scoped, filtered audit events with bounded pagination. */
    @GetMapping
    @Operation(summary = "List audit events", description = "Librarians see all events. Assistants see student and faculty account events plus book and loan events. Dates use Asia/Manila local time.")
    @ErrorApiResponse(responseCode = "400", description = "Invalid filter or sort")
    @ErrorApiResponse(responseCode = "403", description = "Caller or role filter is outside staff scope")
    public ResponseEntity<ApiResponse<Page<AuditEventDTO>>> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) UserRole targetRole,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @ParameterObject @PageableDefault(size = 12, sort = "occurredAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(authentication.getName(), actor,
                entityType, actorRole, targetRole, from, to, pageable)));
    }
}
