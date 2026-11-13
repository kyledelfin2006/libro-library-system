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

# 4. Synthetic catalog: well-known public-domain titles, one physical copy each.
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
add_book "Pride and Prejudice" "Jane Austen" "Fiction" "Demo record: a classic novel of manners." 450.00
add_book "Frankenstein" "Mary Shelley" "Fiction" "Demo record: a gothic science-fiction novel." 400.00
add_book "Dracula" "Bram Stoker" "Fiction" "Demo record: an epistolary gothic horror novel." 420.00
add_book "The Adventures of Sherlock Holmes" "Arthur Conan Doyle" "Mystery" "Demo record: twelve detective short stories." 380.00
add_book "Treasure Island" "Robert Louis Stevenson" "Adventure" "Demo record: a pirate adventure." 350.00
add_book "Moby-Dick" "Herman Melville" "Fiction" "Demo record: a whaling voyage and an obsession." 500.00
add_book "The Time Machine" "H. G. Wells" "Science Fiction" "Demo record: a traveler explores the far future." 330.00
add_book "Jane Eyre" "Charlotte Bronte" "Fiction" "Demo record: a governess comes of age." 440.00
add_book "Alice in Wonderland" "Lewis Carroll" "Fantasy" "Demo record: a girl falls down a rabbit hole." 310.00
add_book "The Art of War" "Sun Tzu" "Philosophy" "Demo record: an ancient treatise on strategy." 360.00
add_book "Meditations" "Marcus Aurelius" "Philosophy" "Demo record: personal notes on Stoic practice." 340.00
add_book "Walden" "Henry David Thoreau" "Nature" "Demo record: reflections on simple living." 370.00
log "seeded 12 demo books"
