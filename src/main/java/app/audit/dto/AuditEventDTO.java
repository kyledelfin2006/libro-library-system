package app.audit.dto;

import app.user.entity.enums.UserRole;
import java.time.LocalDateTime;

/** Read-only audit fields safe for library staff. */
public record AuditEventDTO(Long id, LocalDateTime occurredAt, String actorUniversityId,
                            String actorRole, UserRole targetRole, String action,
                            String entityType, String entityId, String summary) {
}
