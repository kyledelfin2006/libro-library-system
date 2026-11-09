package app.audit.repository;

import app.audit.entity.AuditEvent;
import app.user.entity.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;

/** Persists and reads scoped fixed-format change events. */
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    /** Applies the assistant's target-role boundary before filters and counting. */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:librarian = true OR (e.entityType IN ('BOOK', 'LOAN') AND e.targetRole IS NULL)
                   OR e.targetRole IN :visibleRoles)
              AND (:actor IS NULL OR e.actorUniversityId = :actor)
              AND (:entityType IS NULL OR e.entityType = :entityType)
              AND (:actorRole IS NULL OR e.actorRole = :actorRole)
              AND (:targetRole IS NULL OR e.targetRole = :targetRole)
              AND e.occurredAt >= COALESCE(:from, e.occurredAt)
              AND e.occurredAt <= COALESCE(:to, e.occurredAt)
            """)
    Page<AuditEvent> search(@Param("librarian") boolean librarian,
                            @Param("visibleRoles") Collection<UserRole> visibleRoles,
                            @Param("actor") String actor, @Param("entityType") String entityType,
                            @Param("actorRole") String actorRole, @Param("targetRole") UserRole targetRole,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                            Pageable pageable);
}
