#!/usr/bin/env bash
# Full client journey for a disposable, freshly seeded local stack. It changes demo account passwords and loan state.
# Usage: SMOKE_STAFF_ID=... SMOKE_STAFF_PASSWORD=... scripts/smoke-journey.sh [BASE_URL]
set -euo pipefail

BASE="${1:-http://127.0.0.1:8080}"
PATRON_ID="${SMOKE_UNIVERSITY_ID:-2025-0001}"
PATRON_PASSWORD="${SMOKE_PASSWORD:-${DEMO_PATRON_PASSWORD:-DemoPatron@2026}}"
FACULTY_ID="${SMOKE_FACULTY_ID:-2025-0002}"
FACULTY_PASSWORD="${SMOKE_FACULTY_PASSWORD:-$PATRON_PASSWORD}"
STAFF_ID="${SMOKE_STAFF_ID:?set SMOKE_STAFF_ID for a library assistant or librarian}"
STAFF_PASSWORD="${SMOKE_STAFF_PASSWORD:?set SMOKE_STAFF_PASSWORD without writing it to a file}"
TEMP_PASSWORD="${SMOKE_TEMP_PASSWORD:-SmokeTemporary1!}"

fail() { printf 'JOURNEY FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'ok   %s\n' "$*"; }

# Keep the body and status separate; never print token or password-bearing responses.
request() {
  local method="$1" path="$2" token="${3:-}" payload="${4:-}" auth=() data=()
  [ -n "$token" ] && auth=(-H "Authorization: Bearer $token")
  [ -n "$payload" ] && data=(-H 'Content-Type: application/json' -d "$payload")
  response="$(curl -sS --max-time 20 -w '\n%{http_code}' -X "$method" "$BASE$path" \
    "${auth[@]}" "${data[@]}")" || fail "$method $path could not connect"
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
}

expect() {
  [ "$code" = "$1" ] || fail "$2 returned HTTP $code (expected $1)"
  printf '%s' "$body" | grep -q '"success":true' || fail "$2 has no success envelope"
  pass "$2: HTTP $1 with success envelope"
}

field() { printf '%s' "$body" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }
number() { printf '%s' "$body" | sed -n "s/.*\"$1\":\([0-9][0-9]*\).*/\1/p"; }

request GET /readyz
[ "$code" = 200 ] || fail "readiness returned HTTP $code"
pass 'database is ready'

request POST /app/auth/login '' "{\"universityId\":\"$PATRON_ID\",\"password\":\"$PATRON_PASSWORD\"}"
expect 200 'patron login'
patron_token="$(field accessToken)"
[ -n "$patron_token" ] || fail 'patron token missing'

request GET /app/users/me "$patron_token"
expect 200 'own profile'
printf '%s' "$body" | grep -q '"mustChangePassword":false' || fail 'profile recovery flag missing'

request GET '/app/books/all?size=12' "$patron_token"
expect 200 'book list'
printf '%s' "$body" | grep -q '"content":\[' || fail 'book page has no content array'
request GET '/app/books/search?type=title&value=Frankenstein' "$patron_token"
expect 200 'book search'
book_id="$(number id)"
[ -n "$book_id" ] || fail 'seeded Frankenstein copy not found'

request POST "/app/loans/$book_id" "$patron_token"
expect 201 'borrow copy'
loan_id="$(number loanId)"
[ -n "$loan_id" ] || fail 'borrow returned no loan ID'
request GET /app/loans/me "$patron_token"
expect 200 'own loan history'
printf '%s' "$body" | grep -q "\"loanId\":$loan_id" || fail 'borrow missing from own history'
request PUT "/app/loans/$loan_id/return" "$patron_token"
expect 200 'return copy'
printf '%s' "$body" | grep -q '"status":"RETURNED"' || fail 'return did not close loan'

# A second patron loan gives staff a real active loan to resolve as lost.
request POST /app/auth/login '' "{\"universityId\":\"$FACULTY_ID\",\"password\":\"$FACULTY_PASSWORD\"}"
expect 200 'faculty login'
faculty_token="$(field accessToken)"
[ -n "$faculty_token" ] || fail 'faculty token missing'
request GET '/app/books/search?type=title&value=Dracula' "$faculty_token"
expect 200 'second book search'
second_book_id="$(number id)"
[ -n "$second_book_id" ] || fail 'seeded Dracula copy not found'
request POST "/app/loans/$second_book_id" "$faculty_token"
expect 201 'faculty borrows copy'
second_loan_id="$(number loanId)"
[ -n "$second_loan_id" ] || fail 'faculty borrow returned no loan ID'

request POST /app/auth/login '' "{\"universityId\":\"$STAFF_ID\",\"password\":\"$STAFF_PASSWORD\"}"
expect 200 'staff login'
staff_token="$(field accessToken)"
[ -n "$staff_token" ] || fail 'staff token missing'
request GET '/app/loans?overdue=true' "$staff_token"
expect 200 'staff overdue filter'
printf '%s' "$body" | grep -q '"content":\[' || fail 'overdue loan page has no content array'
request PUT "/app/loans/$second_loan_id/lost" "$staff_token"
expect 200 'mark loan lost'
printf '%s' "$body" | grep -q '"status":"LOST"' || fail 'loss resolution did not close loan'

request PUT "/app/users/$PATRON_ID/password/reset" "$staff_token" \
  "{\"temporaryPassword\":\"$TEMP_PASSWORD\"}"
expect 200 'staff-assisted password reset'
request POST /app/auth/login '' "{\"universityId\":\"$PATRON_ID\",\"password\":\"$TEMP_PASSWORD\"}"
expect 200 'temporary password login'
printf '%s' "$body" | grep -q '"mustChangePassword":true' || fail 'login did not require a password change'
patron_token="$(field accessToken)"
request PUT "/app/users/$PATRON_ID/password" "$patron_token" \
  "{\"currentPassword\":\"$TEMP_PASSWORD\",\"newPassword\":\"$PATRON_PASSWORD\"}"
expect 200 'owner changes temporary password'
request POST /app/auth/login '' "{\"universityId\":\"$PATRON_ID\",\"password\":\"$PATRON_PASSWORD\"}"
expect 200 'restored patron login'
printf '%s' "$body" | grep -q '"mustChangePassword":false' || fail 'password-change flag did not clear'

request GET /v3/api-docs
[ "$code" = 200 ] || fail 'OpenAPI document is not enabled'
printf '%s' "$body" | grep -q '/app/users/{universityId}/password/reset' || fail 'reset route missing from OpenAPI'
pass 'OpenAPI includes the reset route'
printf 'Full journey passed for %s\n' "$BASE"
