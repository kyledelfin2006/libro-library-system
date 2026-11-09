CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    actor_university_id VARCHAR(20) NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    target_role VARCHAR(20),
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id VARCHAR(30) NOT NULL,
    summary VARCHAR(100) NOT NULL
);

CREATE INDEX idx_audit_occurred_at ON audit_event (occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id);
