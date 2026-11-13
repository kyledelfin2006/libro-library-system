#!/usr/bin/env bash
# Install a built web UI into ui/dist, the directory the Caddy proxy container serves read-only at /srv/ui.
#
# Usage: tar -C ui/dist -czf - . | scripts/install-ui.sh        (the CD workflow pipes the artifact in over SSH)
# Environment:
#   LIBRO_UI_DIR  destination directory (default: <repository>/ui/dist)
#
# The directory is updated in place, never replaced: Compose bind-mounts the directory itself, so swapping in a new
# directory would leave the running proxy looking at the old one. Order matters for visitors mid-update: new files
# first (hashed assets never collide with old ones), index.html next, stale files last.
set -euo pipefail
# shellcheck disable=SC1091
. "$(dirname "${BASH_SOURCE[0]}")/common.sh"

dest="${LIBRO_UI_DIR:-$ROOT/ui/dist}"
mkdir -p "$dest"
# Stage beside the destination so a failed or partial upload never touches what is being served.
staging="$(mktemp -d "$(dirname "$dest")/.ui-incoming.XXXXXX")"
trap 'rm -rf "$staging"' EXIT

tar -xzf - -C "$staging"
[ -f "$staging/index.html" ] || die "the uploaded UI has no index.html; leaving $dest unchanged"

# 1. Everything except index.html (hashed assets and static files).
find "$staging" -mindepth 1 -maxdepth 1 ! -name index.html -exec cp -a {} "$dest/" \;
# 2. The entry page, once the assets it references are in place.
cp -a "$staging/index.html" "$dest/index.html"
# 3. Remove files that are no longer part of the build, then any directories that emptied.
(cd "$dest" && find . -type f) | while read -r file; do
  [ -e "$staging/$file" ] || rm -f "$dest/$file"
done
find "$dest" -mindepth 1 -type d -empty -delete

log "Installed the web UI into $dest ($(find "$dest" -type f | wc -l | tr -d ' ') files)"
