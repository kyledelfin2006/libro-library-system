# Demo Accounts

Libro has two kinds of demo accounts. They differ in who may know the password.

## Public demo patrons (live demo)

These accounts exist on the public demo instance so a reviewer can try the API. They are patrons only: they can browse the catalog and borrow within the normal loan rules, and they cannot manage books or accounts. Their data is synthetic and the demo can be reset from a backup. The password is published on purpose.

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

On the **live demo** these accounts exist, but each has its own strong, unique password stored only on the host. Staff credentials are not published, because a staff login can edit or delete catalog and account data.

For **local development** you may seed them with the documented sample passwords below. These are for a private machine only; **never use them on a reachable deployment**.

| Role | University ID | Local-only sample password |
| --- | --- | --- |
| LIBRARIAN | `0000-0000` | `Librarian@0000` |
| ADMINISTRATOR (library assistant) | `0000-0001` | `Assistant@0001` |

To seed them yourself, generate BCrypt hashes of the passwords (single-quote them in `.env`, since hashes contain `$`) and set `LIBRO_ADMIN_PASSWORD_HASH` and the five `LIBRO_BOOTSTRAP_LIBRARIAN_*` variables (see `.env.example`). Students can also sign up publicly at `POST /app/users/signup`; the librarian creates faculty and library-assistant accounts. The production procedure is in the [Operations Runbook](../devops/runbook.md).
