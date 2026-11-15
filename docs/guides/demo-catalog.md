# Demo Catalog

The demo seed loads **50 synthetic book records** on an academic theme: software engineering, cybersecurity, networking, and project management. Titles are well-known real references, but every record is demo data: each description starts with "Demo record:", and replacement costs are made up (₱500 to ₱3,000, shown in Philippine pesos). Each record is one physical copy (see [Domain Decisions](domain-decisions.md)).

Try it with the [demo patron accounts](demo-accounts.md): sign in as the student `2025-0001` or faculty `2025-0002`, then browse, search, filter by genre, and borrow.

## Resetting the local demo

```bash
bash scripts/reset-demo.sh
```

Wipes the local database volume, rebuilds and starts the stack, waits until it is healthy, then runs the seed above. It asks for confirmation (`--yes` skips it), reads the librarian password from the git-ignored `SEED-ACCOUNTS.local.txt` when `SEED_LIBRARIAN_PASSWORD` is unset, and publishes the database on port 5433 if 5432 is already in use. Local development only; it deletes data.

## Loading the catalog

```bash
bash scripts/seed-demo.sh
```

The full seed prompts for the librarian password because adding books is a staff action. `--patrons-only` (used by `deploy.sh`) creates only the demo student and adds no books.

- **Empty catalog only.** If any books exist, the seed skips this step.
- **`--force-books`** adds all 50 again, so each title gets a second copy. It does not replace existing books.
- **Not replaced on upgrade.** A host seeded with the earlier 12 classic novels keeps them; the 50 academic titles are added only to an empty catalog or with `--force-books`.
- **No loans** are created. Every book starts available.

The script (`scripts/seed-demo.sh`) is the source of truth for this list; update this page if the list changes.

## Contents

| Genre | Count |
| --- | --- |
| Software Engineering | 14 |
| Cybersecurity | 13 |
| Networking | 12 |
| Project Management | 11 |

### Software Engineering

| Title | Author | Replacement cost |
| --- | --- | ---: |
| Clean Code | Robert C. Martin | ₱1,300.00 |
| The Pragmatic Programmer | David Thomas and Andrew Hunt | ₱1,400.00 |
| Design Patterns | Erich Gamma et al. | ₱1,650.00 |
| Refactoring | Martin Fowler | ₱1,550.00 |
| Code Complete | Steve McConnell | ₱1,600.00 |
| Domain-Driven Design | Eric Evans | ₱1,850.00 |
| Clean Architecture | Robert C. Martin | ₱1,250.00 |
| Working Effectively with Legacy Code | Michael Feathers | ₱1,450.00 |
| Test-Driven Development: By Example | Kent Beck | ₱1,100.00 |
| Designing Data-Intensive Applications | Martin Kleppmann | ₱1,750.00 |
| Effective Java | Joshua Bloch | ₱1,350.00 |
| Software Engineering | Ian Sommerville | ₱2,400.00 |
| Introduction to Algorithms | Thomas H. Cormen et al. | ₱3,000.00 |
| Continuous Delivery | Jez Humble and David Farley | ₱1,500.00 |

### Cybersecurity

| Title | Author | Replacement cost |
| --- | --- | ---: |
| Security Engineering | Ross Anderson | ₱2,250.00 |
| The Web Application Hacker's Handbook | Dafydd Stuttard and Marcus Pinto | ₱1,650.00 |
| Applied Cryptography | Bruce Schneier | ₱1,900.00 |
| Cryptography Engineering | Niels Ferguson et al. | ₱1,600.00 |
| Computer Security: Principles and Practice | William Stallings and Lawrie Brown | ₱2,750.00 |
| Hacking: The Art of Exploitation | Jon Erickson | ₱1,150.00 |
| The Practice of Network Security Monitoring | Richard Bejtlich | ₱1,250.00 |
| Threat Modeling: Designing for Security | Adam Shostack | ₱1,400.00 |
| Practical Malware Analysis | Michael Sikorski and Andrew Honig | ₱1,500.00 |
| Security in Computing | Charles P. Pfleeger et al. | ₱2,500.00 |
| The Tangled Web | Michal Zalewski | ₱1,000.00 |
| Serious Cryptography | Jean-Philippe Aumasson | ₱1,100.00 |
| Blue Team Handbook | Don Murdoch | ₱500.00 |

### Networking

| Title | Author | Replacement cost |
| --- | --- | ---: |
| Computer Networking: A Top-Down Approach | James F. Kurose and Keith W. Ross | ₱2,850.00 |
| Computer Networks | Andrew S. Tanenbaum et al. | ₱2,650.00 |
| TCP/IP Illustrated, Volume 1 | W. Richard Stevens and Kevin R. Fall | ₱2,150.00 |
| Computer Networks: A Systems Approach | Larry L. Peterson and Bruce S. Davie | ₱2,350.00 |
| Network Warrior | Gary A. Donahue | ₱1,400.00 |
| Data Communications and Networking | Behrouz A. Forouzan | ₱2,500.00 |
| Interconnections | Radia Perlman | ₱1,750.00 |
| Routing TCP/IP, Volume 1 | Jeff Doyle and Jennifer Carroll | ₱2,000.00 |
| High Performance Browser Networking | Ilya Grigorik | ₱900.00 |
| UNIX Network Programming, Volume 1 | W. Richard Stevens et al. | ₱2,100.00 |
| Network Programmability and Automation | Jason Edelman et al. | ₱1,600.00 |
| Wireless Communications | Andrea Goldsmith | ₱2,250.00 |

### Project Management

| Title | Author | Replacement cost |
| --- | --- | ---: |
| A Guide to the Project Management Body of Knowledge | Project Management Institute | ₱2,150.00 |
| The Mythical Man-Month | Frederick P. Brooks Jr. | ₱1,000.00 |
| Peopleware | Tom DeMarco and Timothy Lister | ₱900.00 |
| Agile Estimating and Planning | Mike Cohn | ₱1,150.00 |
| Scrum: The Art of Doing Twice the Work in Half the Time | Jeff Sutherland | ₱600.00 |
| The Phoenix Project | Gene Kim et al. | ₱650.00 |
| Making Things Happen | Scott Berkun | ₱850.00 |
| Accelerate | Nicole Forsgren et al. | ₱750.00 |
| Kanban | David J. Anderson | ₱1,000.00 |
| Project Management: A Systems Approach | Harold Kerzner | ₱2,900.00 |
| Rapid Development | Steve McConnell | ₱1,350.00 |

