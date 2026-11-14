#!/usr/bin/env bash
# Create the public demo accounts and a small synthetic catalog through the API. Idempotent: existing accounts are
# kept, and books are added only when the catalog is empty (or with --force-books).
#
# Usage: scripts/seed-demo.sh [--patrons-only] [--force-books]
#   --patrons-only  create only the demo student through public signup (no staff credentials needed; deploy.sh uses it)
# Environment:
#   LIBRO_API_URL            API base URL (default http://127.0.0.1:8080, the loopback app port on the VM)
#   DEMO_PATRON_PASSWORD     password for the demo patrons (published on purpose; they have no staff rights)
#   SEED_LIBRARIAN_ID        librarian university ID (default 0000-0000)
#   SEED_LIBRARIAN_PASSWORD  librarian password; prompted for when unset, so it never has to be stored on disk
# Demo data is synthetic. Staff credentials on a public host must stay private.
set -euo pipefail

API="${LIBRO_API_URL:-http://127.0.0.1:8080}"
DEMO_PASSWORD="${DEMO_PATRON_PASSWORD:-DemoPatron@2026}"
patrons_only=0; force_books=0
for arg in "$@"; do
  case "$arg" in
    --patrons-only) patrons_only=1 ;;
    --force-books) force_books=1 ;;
    *) echo "unknown option: $arg" >&2; exit 1 ;;
  esac
done

log() { printf '%s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

# POST JSON and print the response body, then a final line holding the HTTP status. Optional third argument: bearer token.
post() {
  local path="$1" body="$2" token="${3:-}" auth=()
  [ -n "$token" ] && auth=(-H "Authorization: Bearer $token")
  curl -sS --max-time 20 -w '\n%{http_code}' -X POST "$API$path" -H 'Content-Type: application/json' \
    "${auth[@]}" -d "$body"
}

# Create an account. 201 is created. The API reports a duplicate university ID as a 400 validation error saying the ID
# is "already registered", which counts as success here so the script can be re-run. Anything else is an error.
create_account() {
  local label="$1" path="$2" body="$3" token="${4:-}" out code
  out="$(post "$path" "$body" "$token")"
  code="${out##*$'\n'}"
  if [ "$code" = "201" ]; then
    log "created $label"
  elif [ "$code" = "409" ] || { [ "$code" = "400" ] && printf '%s' "$out" | grep -q 'already registered'; }; then
    log "kept existing $label"
  else
    die "creating $label failed (HTTP $code): ${out%$'\n'*}"
  fi
}

# 1. Demo student through the public signup route; the role is fixed to STUDENT by the API.
create_account "demo student 2025-0001" /app/users/signup "{\"universityId\":\"2025-0001\",\"password\":\"$DEMO_PASSWORD\",\"firstName\":\"Demo\",\"lastName\":\"Student\",\"email\":\"demo.student@library.local\",\"userCourse\":\"IS\"}"
[ "$patrons_only" -eq 1 ] && exit 0

# 2. Staff-only steps need a librarian token. Prompt rather than reading a stored password.
librarian_id="${SEED_LIBRARIAN_ID:-0000-0000}"
librarian_password="${SEED_LIBRARIAN_PASSWORD:-}"
if [ -z "$librarian_password" ]; then
  printf 'Librarian password for %s: ' "$librarian_id" >&2
  read -rs librarian_password; echo >&2
fi
login="$(post /app/auth/login "{\"universityId\":\"$librarian_id\",\"password\":\"$librarian_password\"}")"
[ "${login##*$'\n'}" = "200" ] || die "librarian login failed (HTTP ${login##*$'\n'})"
token="$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
[ -n "$token" ] || die "librarian login returned no token"

# 3. Demo faculty account, created by the librarian.
create_account "demo faculty 2025-0002" /app/users/faculty "{\"universityId\":\"2025-0002\",\"password\":\"$DEMO_PASSWORD\",\"firstName\":\"Demo\",\"lastName\":\"Faculty\",\"email\":\"demo.faculty@library.local\"}" "$token"

# The librarian create endpoint sets mustChangePassword, which would send the published demo faculty login to the
# forced-change screen. The owner clears it by "changing" the password to the same published value (the API has no
# rule against that). Re-runs skip this when the flag is already false.
faculty_login="$(post /app/auth/login "{\"universityId\":\"2025-0002\",\"password\":\"$DEMO_PASSWORD\"}")"
if [ "${faculty_login##*$'\n'}" = "200" ] && printf '%s' "$faculty_login" | grep -q '"mustChangePassword":true'; then
  faculty_token="$(printf '%s' "$faculty_login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [ -n "$faculty_token" ] || die "demo faculty login returned no token"
  change="$(curl -sS --max-time 20 -w '\n%{http_code}' -X PUT "$API/app/users/2025-0002/password" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $faculty_token" \
    -d "{\"currentPassword\":\"$DEMO_PASSWORD\",\"newPassword\":\"$DEMO_PASSWORD\"}")"
  [ "${change##*$'\n'}" = "200" ] || die "clearing the demo faculty password-change flag failed (HTTP ${change##*$'\n'})"
  log "cleared the password-change flag for demo faculty 2025-0002"
elif [ "${faculty_login##*$'\n'}" != "200" ]; then
  log "note: demo faculty login returned HTTP ${faculty_login##*$'\n'} (an existing account may use a different password); flag left as is"
fi

# 4. Synthetic catalog: 50 well-known academic titles (software engineering, cybersecurity, networking, project
#    management), one physical copy each.
existing="$(curl -sS --max-time 20 "$API/app/books/all?size=1" -H "Authorization: Bearer $token" | sed -n 's/.*"totalElements":\([0-9]*\).*/\1/p')"
if [ "${existing:-0}" -gt 0 ] && [ "$force_books" -ne 1 ]; then
  log "catalog already has $existing books; skipping (use --force-books to add more)"
  exit 0
fi

# Add one book record; any status other than 201 is an error.
add_book() {
  local out code
  out="$(post /app/books/add "{\"title\":\"$1\",\"author\":\"$2\",\"genre\":\"$3\",\"description\":\"$4\",\"replacementCost\":$5}" "$token")"
  code="${out##*$'\n'}"
  [ "$code" = "201" ] || die "adding \"$1\" failed (HTTP $code): ${out%$'\n'*}"
}
# Software Engineering
add_book "Clean Code" "Robert C. Martin" "Software Engineering" "Demo record: writing readable, maintainable code." 2450.00
add_book "The Pragmatic Programmer" "David Thomas and Andrew Hunt" "Software Engineering" "Demo record: practical habits of effective developers." 2600.00
add_book "Design Patterns" "Erich Gamma et al." "Software Engineering" "Demo record: reusable object-oriented design solutions." 2900.00
add_book "Refactoring" "Martin Fowler" "Software Engineering" "Demo record: improving the design of existing code." 2750.00
add_book "Code Complete" "Steve McConnell" "Software Engineering" "Demo record: a handbook of software construction." 2800.00
add_book "Domain-Driven Design" "Eric Evans" "Software Engineering" "Demo record: modeling complex business domains in software." 3100.00
add_book "Clean Architecture" "Robert C. Martin" "Software Engineering" "Demo record: structuring systems around stable boundaries." 2400.00
add_book "Working Effectively with Legacy Code" "Michael Feathers" "Software Engineering" "Demo record: safely changing untested code." 2650.00
add_book "Test-Driven Development: By Example" "Kent Beck" "Software Engineering" "Demo record: designing code through tests written first." 2200.00
add_book "Designing Data-Intensive Applications" "Martin Kleppmann" "Software Engineering" "Demo record: reliable, scalable data systems." 3000.00
add_book "Effective Java" "Joshua Bloch" "Software Engineering" "Demo record: best practices for the Java platform." 2500.00
add_book "Software Engineering" "Ian Sommerville" "Software Engineering" "Demo record: a survey textbook of the software lifecycle." 3800.00
add_book "Introduction to Algorithms" "Thomas H. Cormen et al." "Software Engineering" "Demo record: a comprehensive algorithms textbook." 4500.00
add_book "Continuous Delivery" "Jez Humble and David Farley" "Software Engineering" "Demo record: automating build, test, and release." 2700.00
# Cybersecurity
add_book "Security Engineering" "Ross Anderson" "Cybersecurity" "Demo record: building dependable secure systems." 3600.00
add_book "The Web Application Hacker's Handbook" "Dafydd Stuttard and Marcus Pinto" "Cybersecurity" "Demo record: finding and exploiting web application flaws." 2900.00
add_book "Applied Cryptography" "Bruce Schneier" "Cybersecurity" "Demo record: protocols, algorithms, and source code." 3200.00
add_book "Cryptography Engineering" "Niels Ferguson et al." "Cybersecurity" "Demo record: design principles for practical cryptography." 2800.00
add_book "Computer Security: Principles and Practice" "William Stallings and Lawrie Brown" "Cybersecurity" "Demo record: a foundational computer security textbook." 4200.00
add_book "Hacking: The Art of Exploitation" "Jon Erickson" "Cybersecurity" "Demo record: low-level exploitation techniques." 2300.00
add_book "The Practice of Network Security Monitoring" "Richard Bejtlich" "Cybersecurity" "Demo record: detecting and responding to intrusions." 2400.00
add_book "Threat Modeling: Designing for Security" "Adam Shostack" "Cybersecurity" "Demo record: identifying threats during design." 2600.00
add_book "Practical Malware Analysis" "Michael Sikorski and Andrew Honig" "Cybersecurity" "Demo record: dissecting malicious software." 2700.00
add_book "Security in Computing" "Charles P. Pfleeger et al." "Cybersecurity" "Demo record: threats, controls, and secure system design." 3900.00
add_book "The Tangled Web" "Michal Zalewski" "Cybersecurity" "Demo record: the security model of modern web browsers." 2100.00
add_book "Serious Cryptography" "Jean-Philippe Aumasson" "Cybersecurity" "Demo record: a practical introduction to modern encryption." 2200.00
add_book "Blue Team Handbook" "Don Murdoch" "Cybersecurity" "Demo record: a field guide to incident response." 1500.00
# Networking
add_book "Computer Networking: A Top-Down Approach" "James F. Kurose and Keith W. Ross" "Networking" "Demo record: networking taught from the application layer down." 4300.00
add_book "Computer Networks" "Andrew S. Tanenbaum et al." "Networking" "Demo record: a classic layered networking textbook." 4100.00
add_book "TCP/IP Illustrated, Volume 1" "W. Richard Stevens and Kevin R. Fall" "Networking" "Demo record: the TCP/IP protocols in detail." 3500.00
add_book "Computer Networks: A Systems Approach" "Larry L. Peterson and Bruce S. Davie" "Networking" "Demo record: networks explained through system design." 3700.00
add_book "Network Warrior" "Gary A. Donahue" "Networking" "Demo record: hands-on enterprise network administration." 2600.00
add_book "Data Communications and Networking" "Behrouz A. Forouzan" "Networking" "Demo record: an introductory data communications textbook." 3900.00
add_book "Interconnections" "Radia Perlman" "Networking" "Demo record: bridges, routers, and switching protocols." 3000.00
add_book "Routing TCP/IP, Volume 1" "Jeff Doyle and Jennifer Carroll" "Networking" "Demo record: interior routing protocols in depth." 3300.00
add_book "High Performance Browser Networking" "Ilya Grigorik" "Networking" "Demo record: web performance across the network stack." 2000.00
add_book "UNIX Network Programming, Volume 1" "W. Richard Stevens et al." "Networking" "Demo record: the sockets networking API." 3400.00
add_book "Network Programmability and Automation" "Jason Edelman et al." "Networking" "Demo record: automating network operations with code." 2800.00
add_book "Wireless Communications" "Andrea Goldsmith" "Networking" "Demo record: principles of wireless system design." 3600.00
# Project Management
add_book "A Guide to the Project Management Body of Knowledge" "Project Management Institute" "Project Management" "Demo record: the standard reference for project management practice." 3500.00
add_book "The Mythical Man-Month" "Frederick P. Brooks Jr." "Project Management" "Demo record: essays on managing software projects." 2100.00
add_book "Peopleware" "Tom DeMarco and Timothy Lister" "Project Management" "Demo record: productive projects and teams." 2000.00
add_book "Agile Estimating and Planning" "Mike Cohn" "Project Management" "Demo record: estimating and planning agile projects." 2300.00
add_book "Scrum: The Art of Doing Twice the Work in Half the Time" "Jeff Sutherland" "Project Management" "Demo record: the origins and practice of Scrum." 1600.00
add_book "The Phoenix Project" "Gene Kim et al." "Project Management" "Demo record: a novel about IT operations and DevOps." 1700.00
add_book "Making Things Happen" "Scott Berkun" "Project Management" "Demo record: mastering project management in practice." 1900.00
add_book "Accelerate" "Nicole Forsgren et al." "Project Management" "Demo record: research on high-performing technology teams." 1800.00
add_book "Kanban" "David J. Anderson" "Project Management" "Demo record: evolutionary change for technology teams." 2100.00
add_book "Project Management: A Systems Approach" "Harold Kerzner" "Project Management" "Demo record: planning, scheduling, and controlling projects." 4400.00
add_book "Rapid Development" "Steve McConnell" "Project Management" "Demo record: taming wild software schedules." 2500.00
log "seeded 50 demo books"
