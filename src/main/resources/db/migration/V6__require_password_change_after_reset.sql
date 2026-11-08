ALTER TABLE users
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
