package app.user.service;

import app.user.entity.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the account authorization matrix without Spring, a database, or HTTP.
 * Each row reads: caller role, target role, whether staff management is allowed.
 */
class UserAccessPolicyTest {

    private static final String CALLER = "2025-0001";
    private static final String OTHER = "2025-0002";

    private final UserAccessPolicy policy = new UserAccessPolicy();

    /** Verifies the staff boundary for managing someone else's account. */
    @ParameterizedTest(name = "{0} managing {1} -> {2}")
    @CsvSource({
            "STUDENT,       STUDENT,       false",
            "FACULTY,       STUDENT,       false",
            "ADMINISTRATOR, STUDENT,       true",
            "ADMINISTRATOR, FACULTY,       true",
            "ADMINISTRATOR, ADMINISTRATOR, false",
            "ADMINISTRATOR, LIBRARIAN,     false",
            "LIBRARIAN,     FACULTY,       true",
            "LIBRARIAN,     ADMINISTRATOR, true",
            "LIBRARIAN,     LIBRARIAN,     false"
    })
    void crossAccountAccessFollowsTheStaffMatrix(UserRole caller, UserRole target, boolean allowed) {
        var authorities = authoritiesOf(caller);
        if (allowed) {
            assertThatCode(() -> policy.requireAccountAccess(CALLER, OTHER, target, authorities))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.requireAccountAccess(CALLER, OTHER, target, authorities))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    /** Pins every caller and target role for staff-assisted recovery. */
    @ParameterizedTest(name = "{0} resetting {1} -> {2}")
    @CsvSource({
            "STUDENT, STUDENT, false", "STUDENT, FACULTY, false", "STUDENT, ADMINISTRATOR, false", "STUDENT, LIBRARIAN, false",
            "FACULTY, STUDENT, false", "FACULTY, FACULTY, false", "FACULTY, ADMINISTRATOR, false", "FACULTY, LIBRARIAN, false",
            "ADMINISTRATOR, STUDENT, true", "ADMINISTRATOR, FACULTY, true", "ADMINISTRATOR, ADMINISTRATOR, false", "ADMINISTRATOR, LIBRARIAN, false",
            "LIBRARIAN, STUDENT, true", "LIBRARIAN, FACULTY, true", "LIBRARIAN, ADMINISTRATOR, true", "LIBRARIAN, LIBRARIAN, false"
    })
    void resetFollowsFullRoleMatrix(UserRole caller, UserRole target, boolean allowed) {
        if (allowed) {
            assertThatCode(() -> policy.requirePasswordReset(CALLER, OTHER, target, authoritiesOf(caller)))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.requirePasswordReset(CALLER, OTHER, target, authoritiesOf(caller)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    /** Staff cannot use recovery to skip proof of their own current password. */
    @ParameterizedTest
    @CsvSource({"ADMINISTRATOR", "LIBRARIAN"})
    void staffCannotResetSelf(UserRole role) {
        assertThatThrownBy(() -> policy.requirePasswordReset(CALLER, CALLER, role, authoritiesOf(role)))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Verifies every role, including the librarian, may manage its own profile and password. */
    @ParameterizedTest
    @CsvSource({"STUDENT", "FACULTY", "ADMINISTRATOR", "LIBRARIAN"})
    void ownerMayAlwaysManageOwnAccount(UserRole role) {
        assertThatCode(() -> policy.requireAccountAccess(CALLER, CALLER, role, authoritiesOf(role)))
                .doesNotThrowAnyException();
    }

    /** Verifies patrons may delete themselves but staff may not, so the library keeps its administrators. */
    @ParameterizedTest(name = "{0} deleting self -> {1}")
    @CsvSource({"STUDENT, true", "FACULTY, true", "ADMINISTRATOR, false", "LIBRARIAN, false"})
    void selfDeletionIsLimitedToPatrons(UserRole role, boolean allowed) {
        var authorities = authoritiesOf(role);
        if (allowed) {
            assertThatCode(() -> policy.requireCanDelete(CALLER, CALLER, role, authorities))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.requireCanDelete(CALLER, CALLER, role, authorities))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    /** Verifies the directory shows administrators only to the librarian. */
    @Test
    void visibleRolesDependOnStaffLevel() {
        assertThat(policy.visibleRoles(authoritiesOf(UserRole.ADMINISTRATOR)))
                .containsExactly(UserRole.STUDENT, UserRole.FACULTY);
        assertThat(policy.visibleRoles(authoritiesOf(UserRole.LIBRARIAN)))
                .containsExactly(UserRole.STUDENT, UserRole.FACULTY, UserRole.ADMINISTRATOR);
    }

    /** Verifies the creation and directory gates, and that missing authorities grant nothing. */
    @Test
    void librarianAndStaffGatesRejectEveryoneElse() {
        assertThatCode(() -> policy.requireLibrarian(authoritiesOf(UserRole.LIBRARIAN))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireLibrarian(authoritiesOf(UserRole.ADMINISTRATOR)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.requireStaff(authoritiesOf(UserRole.ADMINISTRATOR))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireStaff(authoritiesOf(UserRole.FACULTY)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireStaff(null)).isInstanceOf(AccessDeniedException.class);
    }

    private static List<GrantedAuthority> authoritiesOf(UserRole role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
