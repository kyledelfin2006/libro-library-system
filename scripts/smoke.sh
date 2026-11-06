#!/usr/bin/env bash
# Post-deploy smoke test. Verifies more than /readyz: that the proxy answers, that a patron can log in, that an
# authenticated read works, and that protected routes still reject anonymous requests.
#
# Usage: scripts/smoke.sh [BASE_URL]
#   BASE_URL defaults to $LIBRO_SMOKE_URL, then https://$LIBRO_DOMAIN.
# Environment:
#   SMOKE_UNIVERSITY_ID / SMOKE_PASSWORD  patron used to log in (default: the public demo student)
#   SMOKE_INSECURE=1                      skip TLS verification (local rehearsals with Caddy's internal CA only)
set -euo pipefail

BASE="${1:-${LIBRO_SMOKE_URL:-https://${LIBRO_DOMAIN:?pass BASE_URL or set LIBRO_DOMAIN}}}"
UNIVERSITY_ID="${SMOKE_UNIVERSITY_ID:-2025-0001}"
PASSWORD="${SMOKE_PASSWORD:-${DEMO_PATRON_PASSWORD:-DemoPatron@2026}}"
CURL=(curl -sS --max-time 15)
[ "${SMOKE_INSECURE:-0}" = "1" ] && CURL+=(-k)

fail() { printf 'SMOKE FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'ok   %s\n' "$*"; }

# Return the HTTP status code of a request (extra curl arguments follow the URL).
status() { local url="$1"; shift; "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@" "$url"; }

# 1. Readiness proves the API can reach PostgreSQL; liveness proves the process is up.
[ "$(status "$BASE/readyz")" = "200" ] || fail "/readyz did not return 200"
pass "/readyz is UP"
[ "$(status "$BASE/livez")" = "200" ] || fail "/livez did not return 200"
pass "/livez is UP"

# 2. A patron can log in and receive a token (exercises Spring Security, BCrypt, and JWT signing).
login_body="$("${CURL[@]}" -X POST "$BASE/app/auth/login" -H 'Content-Type: application/json' \
  -d "{\"universityId\":\"$UNIVERSITY_ID\",\"password\":\"$PASSWORD\"}")" || fail "login request failed"
token="$(printf '%s' "$login_body" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
[ -n "$token" ] || fail "login returned no access token for $UNIVERSITY_ID"
pass "login issued a token for $UNIVERSITY_ID"

# 3. An authenticated read goes through the JWT filter, the service, and the database.
code="$(status "$BASE/app/books/all?size=1" -H "Authorization: Bearer $token")"
[ "$code" = "200" ] || fail "authenticated book list returned HTTP $code"
pass "authenticated read returned 200"

# 4. The same route must reject an anonymous request.
code="$(status "$BASE/app/books/all?size=1")"
[ "$code" = "401" ] || fail "anonymous book list returned HTTP $code instead of 401"
pass "anonymous read is rejected (401)"

printf 'Smoke test passed for %s\n' "$BASE"
