#!/usr/bin/env bash
# Back up the PostgreSQL database to a compressed custom-format dump
# and prune old local copies.
#
# Usage: scripts/backup.sh
# Environment (from the private .env):
#   LIBRO_DATA_DIR       block-volume mount; dumps go to $LIBRO_DATA_DIR/backups unless LIBRO_BACKUP_DIR is set
#   LIBRO_BACKUP_KEEP    number of local dumps to keep (default 7)
set -euo pipefail
# shellcheck disable=SC1091
. "$(dirname "${BASH_SOURCE[0]}")/common.sh"

load_env
require_vars POSTGRES_USER POSTGRES_DB LIBRO_DATA_DIR

dir="${LIBRO_BACKUP_DIR:-$LIBRO_DATA_DIR/backups}"
keep="${LIBRO_BACKUP_KEEP:-7}"
mkdir -p "$dir"
chmod 700 "$dir" 2>/dev/null || true

name="libro-$(date -u +%Y%m%dT%H%M%SZ).dump"
tmp="$dir/$name.partial"

# Dump through the running database container. -Fc is compressed and restorable with pg_restore.
log "Dumping $POSTGRES_DB to $dir/$name"
if ! compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$tmp"; then
  rm -f "$tmp"
  die "pg_dump failed"
fi
[ -s "$tmp" ] || { rm -f "$tmp"; die "pg_dump produced an empty file"; }
# Only a complete dump gets its final name, so a half-written file is never mistaken for a backup.
mv "$tmp" "$dir/$name"
chmod 600 "$dir/$name" 2>/dev/null || true

# Keep the newest N local dumps.
# shellcheck disable=SC2012
ls -1t "$dir"/libro-*.dump 2>/dev/null | tail -n +"$((keep + 1))" | while read -r old; do rm -f "$old"; done

log "Backup complete: $dir/$name ($(wc -c < "$dir/$name") bytes)"
printf '%s\n' "$dir/$name"
