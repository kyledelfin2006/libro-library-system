#!/usr/bin/env bash
# Deploy the application to this host with Docker Compose, verify it, and roll back automatically on failure.
#
# Usage: scripts/deploy.sh [--image REF]
#   --image REF  deploy an already-built image (a registry reference pinned by digest). Without it, the image is
#                built from the checked-out source and tagged libro-api:<git commit>.
#
# Steps: preflight checks -> back up the database (if it is running) -> start the new image -> wait for health ->
#        create the demo student -> smoke test -> record the image as live. A failure at or after the start step
#        restarts the previously recorded image. Flyway migrations are forward-only, so if the new version applied
#        a schema change, restore the pre-deploy backup (scripts/restore.sh) rather than relying on the rollback.
#
# Environment (private .env on the host): see docs/devops/runbook.md.
#   SMOKE_INSECURE=1  skip TLS verification in the smoke test (local rehearsals with Caddy's internal CA only)
#   LIBRO_ALLOW_DIRTY=1  allow building from a working tree with uncommitted changes (rehearsals only)
#   LIBRO_ALLOW_UNPINNED=1  allow --image without an @sha256 digest (rehearsals only; releases must be immutable)
set -euo pipefail
# shellcheck disable=SC1091
. "$(dirname "${BASH_SOURCE[0]}")/common.sh"

image_arg=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --image) image_arg="${2:-}"; [ -n "$image_arg" ] || die "--image needs a value"; shift 2 ;;
    *) die "unknown option: $1" ;;
  esac
done

# A release image must be immutable: a tag can be re-pointed, a digest cannot.
case "$image_arg" in
  ""|*@sha256:*) ;;
  *) [ "${LIBRO_ALLOW_UNPINNED:-0}" = "1" ] || die "--image must be pinned by digest (name@sha256:...); got: $image_arg" ;;
esac

load_env
require_vars POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD LIBRO_JWT_SECRET_BASE64 LIBRO_DOMAIN LIBRO_DATA_DIR

# --- Preflight: refuse unsafe configuration before touching the running stack ---------------------------------------
[ "$LIBRO_DOMAIN" != "localhost" ] || [ "${LIBRO_ALLOW_LOCALHOST:-0}" = "1" ] \
  || die "LIBRO_DOMAIN is 'localhost'; set the public host name so Caddy can issue a certificate"

# The environment file holds secrets, so group and others must not be able to read it.
if [ "${LIBRO_SKIP_PERM_CHECK:-0}" != "1" ]; then
  perms="$(stat -c %a "$ENV_FILE" 2>/dev/null || echo "")"
  case "$perms" in
    *00) ;;
    *) die "$ENV_FILE has mode ${perms:-unknown}; run: chmod 600 $ENV_FILE" ;;
  esac
fi

[ -d "$LIBRO_DATA_DIR/postgres" ] \
  || die "$LIBRO_DATA_DIR/postgres does not exist; create it on the block volume and give it to UID 70 (see the runbook)"

if [ "${LIBRO_API_DOCS_ENABLED:-false}" = "true" ]; then
  log "NOTE: Swagger UI and /v3/api-docs are enabled (intended for the synthetic-data demo only)."
fi

state="$(state_dir)"
mkdir -p "$state"

# --- Resolve the image to deploy -------------------------------------------------------------------------------------
if [ -n "$image_arg" ]; then
  new_image="$image_arg"
else
  cd "$ROOT"
  if [ -n "$(git status --porcelain 2>/dev/null)" ] && [ "${LIBRO_ALLOW_DIRTY:-0}" != "1" ]; then
    die "working tree has uncommitted changes; commit or stash them (or set LIBRO_ALLOW_DIRTY=1 for a rehearsal)"
  fi
  new_image="libro-api:$(git rev-parse --short=12 HEAD)"
  log "Building $new_image"
  docker build -t "$new_image" "$ROOT"
fi

previous_image="$(cat "$state/current-image" 2>/dev/null || true)"
log "Previous image: ${previous_image:-none (first deploy)}"
log "New image:      $new_image"

# --- Back up before changing anything (skipped on the first deploy, when no database exists yet) -----------------------
if [ -n "$(compose ps --status running -q db 2>/dev/null)" ]; then
  log "Backing up the database before deploying"
  "$(dirname "${BASH_SOURCE[0]}")/backup.sh" >/dev/null
fi

# Restart the previously recorded image after a failed deploy.
rollback() {
  if [ -z "$previous_image" ]; then
    log "No previous image is recorded, so there is nothing to roll back to. Inspect: docker compose logs app"
    return 1
  fi
  log "Rolling back to $previous_image"
  (export LIBRO_IMAGE="$previous_image"; compose up -d --no-build app proxy)
  if wait_healthy library-app 240; then
    log "Rolled back to $previous_image and it is healthy."
    log "If the failed release applied a Flyway migration, restore the pre-deploy backup with scripts/restore.sh."
  else
    log "The previous image is not healthy either; investigate with: docker compose logs app"
  fi
}

# --- Deploy ----------------------------------------------------------------------------------------------------------
export LIBRO_IMAGE="$new_image"
if ! compose up -d --no-build --remove-orphans; then
  log "docker compose up failed"
  rollback || true
  exit 1
fi

if ! wait_healthy library-app 240; then
  log "The new image did not become healthy"
  rollback || true
  exit 1
fi

# The smoke test logs in as the public demo student, so make sure that account exists. Idempotent, no secrets needed.
"$(dirname "${BASH_SOURCE[0]}")/seed-demo.sh" --patrons-only

smoke_url="${LIBRO_SMOKE_URL:-https://$LIBRO_DOMAIN}"
if ! "$(dirname "${BASH_SOURCE[0]}")/smoke.sh" "$smoke_url"; then
  log "Smoke test failed"
  rollback || true
  exit 1
fi

# --- Record the live image so the next deploy can roll back to it ---------------------------------------------------
printf '%s\n' "$previous_image" > "$state/previous-image"
printf '%s\n' "$new_image" > "$state/current-image"
log "Deployed $new_image"
