package app.audit.service;

import app.audit.dto.AuditEventDTO;
import app.audit.entity.AuditEvent;
import app.audit.repository.AuditRepository;
import app.global.exception.InvalidRequestException;
import app.global.time.LibraryTime;
import app.user.entity.enums.UserRole;
import app.user.exception.UserNotFoundException;
import app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

/** Writes controlled change records and applies staff scope to audit reads. */
@Service
@RequiredArgsConstructor
public class AuditService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "occurredAt", "action", "entityType");
    private final AuditRepository repository;
    private final UserRepository users;

    /** Records an event inside the caller's write transaction, so rollback removes it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, String entityId, UserRole targetRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName());
        String actor = anonymous
                ? ("ACCOUNT_CREATED".equals(action) && targetRole == UserRole.STUDENT ? entityId : "system")
                : auth.getName();
        String actorRole = anonymous
                ? (auth == null ? "SYSTEM" : "ANONYMOUS") : auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse("UNKNOWN");
        // Summary comes only from a fixed action token, never from user-controlled content.
        repository.save(new AuditEvent(LibraryTime.now(), actor, actorRole, targetRole,
                action, entityType, entityId, action.replace('_', ' ').toLowerCase(java.util.Locale.ROOT)));
    }

    /** Returns only events the persisted staff role may see, with filters applied before paging. */
    @Transactional(readOnly = true)
    public Page<AuditEventDTO> list(String callerId, String actor, String entityType,
            String actorRole, UserRole targetRole, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        UserRole role = users.findByUniversityId(callerId)
                .orElseThrow(() -> new UserNotFoundException("User not found")).getUserRole();
        if (role != UserRole.LIBRARIAN && role != UserRole.ADMINISTRATOR) {
            throw new AccessDeniedException("Only library staff may read audit events");
        }
        if (role == UserRole.ADMINISTRATOR && (targetRole == UserRole.ADMINISTRATOR
                || targetRole == UserRole.LIBRARIAN || "ADMINISTRATOR".equals(actorRole)
                || "LIBRARIAN".equals(actorRole))) {
            throw new AccessDeniedException("Access denied");
        }
        if (pageable == null || pageable.getSort().stream().anyMatch(o -> !SORT_FIELDS.contains(o.getProperty()))
                || (from != null && to != null && from.isAfter(to))) {
            throw new InvalidRequestException("Invalid audit pagination, sort, or date range");
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "occurredAt");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable safe = PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize()), sort);
        return repository.search(role == UserRole.LIBRARIAN,
                java.util.List.of(UserRole.STUDENT, UserRole.FACULTY), actor, entityType, actorRole,
                targetRole, from, to, safe).map(e -> new AuditEventDTO(e.getId(), e.getOccurredAt(),
                e.getActorUniversityId(), e.getActorRole(), e.getTargetRole(), e.getAction(),
                e.getEntityType(), e.getEntityId(), e.getSummary()));
    }
}
