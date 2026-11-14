# Demo Catalog

The demo seed loads **50 synthetic book records** on an academic theme: software engineering, cybersecurity, networking, and project management. Titles are well-known real references, but every record is demo data: each description starts with "Demo record:", and replacement costs are made up. Each record is one physical copy (see [Domain Decisions](domain-decisions.md)).

Try it with the [demo patron accounts](demo-accounts.md): sign in as the student `2025-0001` or faculty `2025-0002`, then browse, search, filter by genre, and borrow.

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
| Clean Code | Robert C. Martin | 2450.00 |
| The Pragmatic Programmer | David Thomas and Andrew Hunt | 2600.00 |
| Design Patterns | Erich Gamma et al. | 2900.00 |
| Refactoring | Martin Fowler | 2750.00 |
| Code Complete | Steve McConnell | 2800.00 |
| Domain-Driven Design | Eric Evans | 3100.00 |
| Clean Architecture | Robert C. Martin | 2400.00 |
| Working Effectively with Legacy Code | Michael Feathers | 2650.00 |
| Test-Driven Development: By Example | Kent Beck | 2200.00 |
| Designing Data-Intensive Applications | Martin Kleppmann | 3000.00 |
| Effective Java | Joshua Bloch | 2500.00 |
| Software Engineering | Ian Sommerville | 3800.00 |
| Introduction to Algorithms | Thomas H. Cormen et al. | 4500.00 |
| Continuous Delivery | Jez Humble and David Farley | 2700.00 |

### Cybersecurity

| Title | Author | Replacement cost |
| --- | --- | ---: |
| Security Engineering | Ross Anderson | 3600.00 |
| The Web Application Hacker's Handbook | Dafydd Stuttard and Marcus Pinto | 2900.00 |
| Applied Cryptography | Bruce Schneier | 3200.00 |
| Cryptography Engineering | Niels Ferguson et al. | 2800.00 |
| Computer Security: Principles and Practice | William Stallings and Lawrie Brown | 4200.00 |
| Hacking: The Art of Exploitation | Jon Erickson | 2300.00 |
| The Practice of Network Security Monitoring | Richard Bejtlich | 2400.00 |
| Threat Modeling: Designing for Security | Adam Shostack | 2600.00 |
| Practical Malware Analysis | Michael Sikorski and Andrew Honig | 2700.00 |
| Security in Computing | Charles P. Pfleeger et al. | 3900.00 |
| The Tangled Web | Michal Zalewski | 2100.00 |
| Serious Cryptography | Jean-Philippe Aumasson | 2200.00 |
| Blue Team Handbook | Don Murdoch | 1500.00 |

### Networking

| Title | Author | Replacement cost |
| --- | --- | ---: |
| Computer Networking: A Top-Down Approach | James F. Kurose and Keith W. Ross | 4300.00 |
| Computer Networks | Andrew S. Tanenbaum et al. | 4100.00 |
| TCP/IP Illustrated, Volume 1 | W. Richard Stevens and Kevin R. Fall | 3500.00 |
| Computer Networks: A Systems Approach | Larry L. Peterson and Bruce S. Davie | 3700.00 |
| Network Warrior | Gary A. Donahue | 2600.00 |
| Data Communications and Networking | Behrouz A. Forouzan | 3900.00 |
| Interconnections | Radia Perlman | 3000.00 |
| Routing TCP/IP, Volume 1 | Jeff Doyle and Jennifer Carroll | 3300.00 |
| High Performance Browser Networking | Ilya Grigorik | 2000.00 |
| UNIX Network Programming, Volume 1 | W. Richard Stevens et al. | 3400.00 |
| Network Programmability and Automation | Jason Edelman et al. | 2800.00 |
| Wireless Communications | Andrea Goldsmith | 3600.00 |

### Project Management

| Title | Author | Replacement cost |
| --- | --- | ---: |
| A Guide to the Project Management Body of Knowledge | Project Management Institute | 3500.00 |
| The Mythical Man-Month | Frederick P. Brooks Jr. | 2100.00 |
| Peopleware | Tom DeMarco and Timothy Lister | 2000.00 |
| Agile Estimating and Planning | Mike Cohn | 2300.00 |
| Scrum: The Art of Doing Twice the Work in Half the Time | Jeff Sutherland | 1600.00 |
| The Phoenix Project | Gene Kim et al. | 1700.00 |
| Making Things Happen | Scott Berkun | 1900.00 |
| Accelerate | Nicole Forsgren et al. | 1800.00 |
| Kanban | David J. Anderson | 2100.00 |
| Project Management: A Systems Approach | Harold Kerzner | 4400.00 |
| Rapid Development | Steve McConnell | 2500.00 |

