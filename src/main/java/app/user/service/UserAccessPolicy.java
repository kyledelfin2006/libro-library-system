package app.user.service;

import app.user.entity.enums.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Decides who may see or change which account. This is the service-level authorization boundary for users.
 * <p>
 * {@code SecurityConfig} gates each route by role, but a route gate cannot know whose account a request targets.
 * These checks run after the target account is loaded, so the decision can use its role and owner. Keeping them in
 * one class makes the rules easy to read, review, and test on their own:
 * </p>
 * <ul>
 *     <li>Every account may read and update its own profile and password.</li>
 *     <li>Administrators (library assistants) may manage student and faculty accounts.</li>
 *     <li>The librarian may also manage administrator accounts.</li>
 *     <li>Nobody may manage another librarian's account.</li>
 *     <li>Students and faculty may delete themselves; staff may not delete their own accounts.</li>
 * </ul>
 * <p>
 * The class is stateless and works only with role names and IDs, so it needs no repository or transaction.
 * </p>
 */
@Component
public class UserAccessPolicy {

    private static final String ADMINISTRATOR = "ROLE_ADMINISTRATOR";
    private static final String LIBRARIAN = "ROLE_LIBRARIAN";

    /**
     * Requires the caller to own the target account or to be staff allowed to manage its role.
     * Used by profile reads, profile updates, and password changes.
     *
     * @param callerId normalized university ID of the authenticated caller
     * @param targetId normalized university ID of the requested account
     * @param targetRole role of the loaded target account
     * @param authorities authorities granted to the caller
     * @throws AccessDeniedException if neither ownership nor a staff rule permits access
     */
    public void requireAccountAccess(String callerId, String targetId, UserRole targetRole,
                                     Collection<? extends GrantedAuthority> authorities) {
        // The owner may always manage their own profile and password, whatever their role.
        if (callerId.equals(targetId) || canStaffManage(targetRole, authorities)) {
            return;
        }
        throw new AccessDeniedException("Access denied");
    }

    /** Requires staff authority for a different account within the caller's existing management scope. */
    public void requirePasswordReset(String callerId, String targetId, UserRole targetRole,
                                     Collection<? extends GrantedAuthority> authorities) {
        // A staff member uses the owner change route for their own password.
        if (callerId.equals(targetId) || !canStaffManage(targetRole, authorities)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Requires the caller to be allowed to delete the target account.
     * <p>
     * This differs from {@link #requireAccountAccess}: owning an account is enough to delete it only for students and
     * faculty, because removing the last staff account could lock the library out of administration.
     * </p>
     *
     * @param callerId normalized university ID of the authenticated caller
     * @param targetId normalized university ID of the account to delete
     * @param targetRole role of the loaded target account
     * @param authorities authorities granted to the caller
     * @throws AccessDeniedException if no rule permits the deletion
     */
    public void requireCanDelete(String callerId, String targetId, UserRole targetRole,
                                 Collection<? extends GrantedAuthority> authorities) {
        boolean ownerCanDelete = callerId.equals(targetId)
                && (targetRole == UserRole.STUDENT || targetRole == UserRole.FACULTY);
        if (!ownerCanDelete && !canStaffManage(targetRole, authorities)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Requires the librarian authority, used to create faculty and administrator accounts.
     *
     * @param authorities authorities granted to the caller
     * @throws AccessDeniedException if the caller is not the librarian
     */
    public void requireLibrarian(Collection<? extends GrantedAuthority> authorities) {
        if (!hasAuthority(authorities, LIBRARIAN)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Requires an administrator or librarian authority, used for the account directory.
     *
     * @param authorities authorities granted to the caller
     * @throws AccessDeniedException if the caller holds neither staff role
     */
    public void requireStaff(Collection<? extends GrantedAuthority> authorities) {
        if (!isStaff(authorities)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Lists the account roles a staff caller may see in the directory.
     *
     * @param authorities authorities granted to the caller
     * @return students and faculty for administrators; the librarian also sees administrators
     */
    public List<UserRole> visibleRoles(Collection<? extends GrantedAuthority> authorities) {
        return hasAuthority(authorities, LIBRARIAN)
                ? List.of(UserRole.STUDENT, UserRole.FACULTY, UserRole.ADMINISTRATOR)
                : List.of(UserRole.STUDENT, UserRole.FACULTY);
    }

    /**
     * Applies the staff target-role boundary shared by access and deletion.
     *
     * @param targetRole role of the account being managed
     * @param authorities authorities granted to the caller
     * @return whether a staff caller may manage an account with that role
     */
    private boolean canStaffManage(UserRole targetRole, Collection<? extends GrantedAuthority> authorities) {
        return switch (targetRole) {
            case STUDENT, FACULTY -> isStaff(authorities);
            case ADMINISTRATOR -> hasAuthority(authorities, LIBRARIAN);
            // Librarian accounts are only ever managed by their owner.
            case LIBRARIAN -> false;
        };
    }

    /** Returns whether the caller is an administrator or the librarian. */
    private boolean isStaff(Collection<? extends GrantedAuthority> authorities) {
        return hasAuthority(authorities, ADMINISTRATOR) || hasAuthority(authorities, LIBRARIAN);
    }

    /** Compares exact Spring authority names; a missing collection grants nothing. */
    private boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String expected) {
        return authorities != null && authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expected::equals);
    }
}
