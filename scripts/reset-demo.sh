#!/usr/bin/env bash
# Reset the LOCAL demo stack to a clean, seeded state: wipe the database volume, rebuild and start the stack, wait for
# it to become healthy, then load the demo accounts and the 50-book catalog.
#
# Usage: scripts/reset-demo.sh [--yes]
#   --yes  skip the confirmation prompt
# Environment:
#   SEED_LIBRARIAN_PASSWORD  librarian password; when unset, read from the git-ignored SEED-ACCOUNTS.local.txt if it
#                            exists, otherwise seed-demo.sh prompts for it
# This DELETES the local database (docker compose down -v). It is for the development stack only; never run it on the
# production host. If another PostgreSQL already uses port 5432, the database is published on 5433 instead.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

log() { printf '%s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

[ -f .env ] || die ".env not found; copy .env.example to .env and fill the required values first"

if [ "${1:-}" != "--yes" ]; then
  printf 'This deletes the local database volume and reseeds it. Continue? [y/N] ' >&2
  read -r answer
  case "$answer" in y|Y|yes|YES) ;; *) log "Cancelled."; exit 1 ;; esac
fi

files=(-f docker-compose.yml)
# Another PostgreSQL on this machine can hold 5432. The app reaches the database over Compose's private network, so only
# the host-side port needs to move.
if (exec 3<>/dev/tcp/127.0.0.1/5432) 2>/dev/null; then
  override="$(mktemp "${TMPDIR:-/tmp}/libro-db-port.XXXXXX.yml")"
  trap 'rm -f "$override"' EXIT
  printf 'services:\n  db:\n    ports: !override\n      - "127.0.0.1:5433:5432"\n' > "$override"
  files+=(-f "$override")
  log "Port 5432 is in use; publishing the database on 127.0.0.1:5433 instead."
fi

log "Wiping the database volume and rebuilding..."
docker compose "${files[@]}" down -v
docker compose "${files[@]}" up -d --build

log "Waiting for the API to become healthy..."
status=""
for _ in $(seq 1 60); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' library-app 2>/dev/null || echo missing)"
  [ "$status" = "healthy" ] && break
  sleep 4
done
[ "$status" = "healthy" ] || die "library-app did not become healthy (last status: $status)"

# Reuse the local seed file for the librarian password so the reset needs no typing. It stays out of git and logs.
if [ -z "${SEED_LIBRARIAN_PASSWORD:-}" ] && [ -f SEED-ACCOUNTS.local.txt ]; then
  SEED_LIBRARIAN_PASSWORD="$(awk '/^LIBRARIAN/{print $4}' SEED-ACCOUNTS.local.txt | tr -d '\r')"
  export SEED_LIBRARIAN_PASSWORD
fi

LIBRO_API_URL="${LIBRO_API_URL:-http://localhost:8080}" bash "$ROOT/scripts/seed-demo.sh"
log "Done. API: http://localhost:8080  UI: run 'npm --prefix ui run dev', then open http://localhost:5173"
