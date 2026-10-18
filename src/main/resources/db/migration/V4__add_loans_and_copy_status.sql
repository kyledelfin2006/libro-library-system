ALTER TABLE books
    ADD COLUMN copy_status VARCHAR(24) NOT NULL DEFAULT 'IN_COLLECTION',
    ADD CONSTRAINT chk_book_copy_status
        CHECK (copy_status IN ('IN_COLLECTION', 'LOST'));

CREATE TABLE loans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    borrowed_at TIMESTAMP NOT NULL,
    due_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    returned_at TIMESTAMP NULL,
    lost_at TIMESTAMP NULL,
    lost_by_user_id BIGINT NULL REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_loan_status
        CHECK (status IN ('ACTIVE', 'RETURNED', 'LOST')),
    CONSTRAINT chk_loan_due_after_borrowed
        CHECK (due_at > borrowed_at),
    CONSTRAINT chk_loan_return_after_borrowed
        CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    CONSTRAINT chk_loan_lost_after_borrowed
        CHECK (lost_at IS NULL OR lost_at >= borrowed_at),
    CONSTRAINT chk_loan_state_fields
        CHECK (
            (status = 'ACTIVE'
                AND returned_at IS NULL
                AND lost_at IS NULL
                AND lost_by_user_id IS NULL)
            OR
            (status = 'RETURNED'
                AND returned_at IS NOT NULL
                AND lost_at IS NULL
                AND lost_by_user_id IS NULL)
            OR
            (status = 'LOST'
                AND returned_at IS NULL
                AND lost_at IS NOT NULL
                AND lost_by_user_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_loans_one_active_per_book
    ON loans (book_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_loans_user_history
    ON loans (user_id, borrowed_at DESC, id DESC);

CREATE INDEX idx_loans_active_user_due
    ON loans (user_id, due_at)
    WHERE status = 'ACTIVE';
