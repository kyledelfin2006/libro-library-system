package app.audit.entity;

import app.user.entity.enums.UserRole;

/**
 * The closed set of changes the audit trail records.
 *
 * <p>The constant name is the stored {@code action} value and, with underscores replaced by spaces, the fixed
 * summary text, so renaming a constant changes what is written for new events. Request text never reaches either
 * value.</p>
 */
public enum AuditAction {
    ACCOUNT_CREATED,
    ACCOUNT_DELETED,
    PROFILE_CHANGED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    BOOK_CREATED,
    BOOK_CHANGED,
    BOOK_DELETED,
    LOAN_MARKED_LOST;

    /**
     * Tells whether an anonymous caller performing this action is acting on their own new account.
     *
     * <p>Public signup creates a student account without any authentication, so the new account is also the
     * actor. Every other anonymous change is attributed to the system.</p>
     *
     * @param targetRole role of the account the event is about, or {@code null} for non-account events
     * @return true when the subject of the event is its own actor
     */
    public boolean actorIsSubject(UserRole targetRole) {
        return this == ACCOUNT_CREATED && targetRole == UserRole.STUDENT;
    }

    /** Returns the fixed, human-readable summary stored with the event. */
    public String summary() {
        return name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }
}
