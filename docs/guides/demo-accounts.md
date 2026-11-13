# Demo Accounts

The planned public demo uses patron accounts with synthetic data. Staff credentials are private. The public demo instance is not deployed yet.

## Public demo patrons (planned)

`scripts/seed-demo.sh` will create these accounts for the public demo after deployment. They are patrons only: they can browse the catalog and borrow within the normal loan rules, and they cannot manage books or accounts. Their data is synthetic and the demo can be reset from a backup. The password is published on purpose. Because the librarian create endpoint requires a first-sign-in password change, the script then signs in as the demo faculty and sets the same password as its owner to clear that flag, so the published login goes straight to the catalog.

| Role | University ID | Password | Created by |
| --- | --- | --- | --- |
| STUDENT | `2025-0001` | `DemoPatron@2026` | Public signup, run by `scripts/seed-demo.sh` |
| FACULTY | `2025-0002` | `DemoPatron@2026` | The librarian, run by `scripts/seed-demo.sh` |

Log in with `POST /app/auth/login` using `universityId` and `password`, then send the returned token as `Authorization: Bearer <token>`. Do not reuse this password anywhere else.

## Staff demo accounts

| Role | University ID | Created by |
| --- | --- | --- |
| LIBRARIAN | `0000-0000` | The opt-in `LIBRO_BOOTSTRAP_LIBRARIAN_*` startup runner |
| ADMINISTRATOR (library assistant) | `0000-0001` | Flyway V2, from `LIBRO_ADMIN_PASSWORD_HASH` |

When deployed, each staff account must have its own strong, unique password stored only on the host. Staff credentials are not published, because a staff login can edit or delete catalog and account data.

For local development, choose private staff passwords, generate their BCrypt hashes (single-quote them in `.env`, since hashes contain `$`), and set `LIBRO_ADMIN_PASSWORD_HASH` and the five `LIBRO_BOOTSTRAP_LIBRARIAN_*` variables (see `.env.example`). Students can also sign up publicly at `POST /app/users/signup`; the librarian creates faculty and library-assistant accounts. The production procedure is in the [Operations Runbook](../devops/runbook.md).
