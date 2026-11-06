#!/usr/bin/env bash
# Shared helpers for the operations scripts. Source this file; do not run it.
# It resolves the repository root, loads the private environment file, and wraps the production Compose command.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The private environment file lives on the host only (never in git). Override it for rehearsals.
ENV_FILE="${LIBRO_ENV_FILE:-$ROOT/.env}"

# Print a message to stderr.
log() { printf '%s\n' "$*" >&2; }

# Print an error and stop.
die() { log "ERROR: $*"; exit 1; }

# Export every variable from the environment file. Values containing `$` (BCrypt hashes) must be single-quoted.
load_env() {
  [ -f "$ENV_FILE" ] || die "environment file not found: $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
}

# Require each named variable to be set and non-empty.
require_vars() {
  local name
  for name in "$@"; do
    [ -n "${!name:-}" ] || die "$name is not set in $ENV_FILE"
  done
}

# Directory that remembers which image is live and which was live before it (used for rollback).
state_dir() { printf '%s\n' "${LIBRO_STATE_DIR:-${LIBRO_DATA_DIR:?LIBRO_DATA_DIR is not set}/state}"; }

# Run Docker Compose against the base file plus the production override, with the proxy profile enabled.
# Commands that do not create containers (exec, ps, stop) still need LIBRO_IMAGE to interpolate, so fall back to the
# recorded live image, then to a harmless placeholder.
compose() {
  if [ -z "${LIBRO_IMAGE:-}" ]; then
    local recorded
    recorded="$(cat "$(state_dir)/current-image" 2>/dev/null || true)"
    export LIBRO_IMAGE="${recorded:-libro-api:unset}"
  fi
  docker compose --env-file "$ENV_FILE" \
    -f "$ROOT/docker-compose.yml" -f "$ROOT/infra/docker-compose.prod.yml" \
    --profile proxy "$@"
}

# Wait until a container reports healthy, or fail after the timeout (seconds).
wait_healthy() {
  local container="$1" timeout="${2:-240}" waited=0 status
  while [ "$waited" -lt "$timeout" ]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container" 2>/dev/null || echo missing)"
    [ "$status" = "healthy" ] && return 0
    sleep 5
    waited=$((waited + 5))
  done
  log "$container did not become healthy within ${timeout}s (last status: ${status:-unknown})"
  return 1
}
