-- Permit the new librarian role in the users table.
ALTER TABLE users
    DROP CONSTRAINT chk_user_role,
    ADD CONSTRAINT chk_user_role
        CHECK (user_role IN ('STUDENT', 'FACULTY', 'ADMINISTRATOR', 'LIBRARIAN'));

-- Librarians are staff accounts, so they cannot have student course or major values.
ALTER TABLE users
    DROP CONSTRAINT chk_student_course,
    ADD CONSTRAINT chk_student_course
        CHECK (
            (user_role IN ('FACULTY', 'ADMINISTRATOR', 'LIBRARIAN')
                AND student_course IS NULL
                AND infotech_major IS NULL)
            OR
            (user_role = 'STUDENT'
                AND student_course IN ('EMC', 'IS', 'IT'))
        );

-- Extend the existing non-student major rule to librarians as well.
ALTER TABLE users
    DROP CONSTRAINT chk_it_major,
    ADD CONSTRAINT chk_it_major
        CHECK (
            (user_role = 'STUDENT'
                AND student_course = 'IT'
                AND infotech_major IN ('SE', 'SMBPO', 'IST', 'HN'))
            OR
            (user_role = 'STUDENT'
                AND student_course IN ('EMC', 'IS')
                AND infotech_major IS NULL)
            OR
            (user_role IN ('FACULTY', 'ADMINISTRATOR', 'LIBRARIAN')
                AND infotech_major IS NULL)
        );
