package app.audit.entity;

import app.user.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** Fixed-format change record; account identifiers remain after account deletion. */
@Entity
@Table(name = "audit_event")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
    @Column(name = "actor_university_id", nullable = false, length = 20)
    private String actorUniversityId;
    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", length = 20)
    private UserRole targetRole;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;
    @Column(name = "entity_id", nullable = false, length = 30)
    private String entityId;
    @Column(nullable = false, length = 100)
    private String summary;

    /** Constructs an event from controlled values only; request text never enters summary. */
    public AuditEvent(LocalDateTime occurredAt, String actorUniversityId, String actorRole,
                      UserRole targetRole, String action, String entityType, String entityId, String summary) {
        this.occurredAt = occurredAt;
        this.actorUniversityId = actorUniversityId;
        this.actorRole = actorRole;
        this.targetRole = targetRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
    }
}
