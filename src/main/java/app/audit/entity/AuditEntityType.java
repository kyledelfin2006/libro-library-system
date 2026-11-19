package app.audit.entity;

/**
 * The kinds of record an audit event can describe.
 *
 * <p>The constant name is the stored {@code entity_type} value. {@code AuditRepository} filters staff reads by
 * these names, so keep them in step with its query.</p>
 */
public enum AuditEntityType {
    USER,
    BOOK,
    LOAN
}
