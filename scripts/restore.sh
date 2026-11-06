#!/usr/bin/env bash
# Restore the database from a dump made by scripts/backup.sh. This REPLACES the current database contents.
#
# Usage: scripts/restore.sh DUMP_FILE [--yes] [--no-safety-backup]
#   --yes                skip the typed confirmation
#   --no-safety-backup   do not back up the current database first
# The application and proxy are stopped during the restore and restarted afterwards.
set -euo pipefail
# shellcheck disable=SC1091
. "$(dirname "${BASH_SOURCE[0]}")/common.sh"

dump="${1:-}"; shift || true
assume_yes=0; safety=1
for arg in "$@"; do
  case "$arg" in
    --yes) assume_yes=1 ;;
    --no-safety-backup) safety=0 ;;
    *) die "unknown option: $arg" ;;
  esac
done
[ -n "$dump" ] && [ -s "$dump" ] || die "usage: scripts/restore.sh DUMP_FILE [--yes] [--no-safety-backup]"

load_env
require_vars POSTGRES_USER POSTGRES_DB LIBRO_DATA_DIR

if [ "$assume_yes" -ne 1 ]; then
  printf 'This replaces ALL data in database "%s". Type the database name to continue: ' "$POSTGRES_DB" >&2
  read -r answer
  [ "$answer" = "$POSTGRES_DB" ] || die "confirmation did not match; nothing was changed"
fi

# Keep a way back: dump the current state before replacing it.
if [ "$safety" -eq 1 ]; then
  log "Taking a safety backup first"
  "$(dirname "${BASH_SOURCE[0]}")/backup.sh" >/dev/null
fi

# Stop writers so nothing changes the database mid-restore. The database container keeps running.
compose stop proxy app

# Restore in a single transaction: either every object is replaced or the database is left as it was.
log "Restoring from $dump"
compose exec -T db pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --clean --if-exists --no-owner --single-transaction < "$dump"

# Restart the application; Flyway validates the restored schema history on startup.
compose up -d --no-build app proxy
wait_healthy library-app 240
log "Restore complete and the application is healthy"
