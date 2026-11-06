#!/usr/bin/env bash
# Vulnerability scan shared by CI (security-scan job) and CD (image job), so both gates apply the same rules.
#
# Usage:
#   scripts/ci/trivy-scan.sh fs              scan the repository's dependency manifests (pom.xml)
#   scripts/ci/trivy-scan.sh image IMAGE     scan a locally built image: OS packages and the application JAR
#
# Policy (identical for both modes):
#   --severity HIGH,CRITICAL  only these levels can fail the build
#   --ignore-unfixed          skip findings with no released fix, since nothing could be upgraded anyway
#   --exit-code 1             any remaining finding fails the step
#   .trivyignore              accepted findings, one CVE per line with a reason and an expiry date
#
# Trivy runs from its official image, pinned by digest, so no third-party GitHub Action has to be trusted and the
# scanner binary cannot change underneath us. To upgrade: docker buildx imagetools inspect aquasec/trivy:<version>
set -euo pipefail

TRIVY_IMAGE="aquasec/trivy:0.58.1@sha256:ab70a02200597efa04748f210f793936eb647cbcdb0ea69cc30b226d6f5a22c7"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY=(--severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 --ignorefile /src/.trivyignore)

mode="${1:?usage: trivy-scan.sh fs | image IMAGE}"
case "$mode" in
  fs)
    # Mount the repository read-only at /src and scan it as a filesystem.
    docker run --rm -v "$ROOT:/src:ro" -w /src "$TRIVY_IMAGE" \
      fs --scanners vuln "${POLICY[@]}" .
    ;;
  image)
    image="${2:?usage: trivy-scan.sh image IMAGE}"
    # The Docker socket lets Trivy read the image from the runner's local Docker daemon, so the image never has to
    # be pushed before it is scanned. The repository mount is only there so .trivyignore applies to images too.
    docker run --rm \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v "$ROOT:/src:ro" -w /src \
      "$TRIVY_IMAGE" image "${POLICY[@]}" "$image"
    ;;
  *)
    echo "unknown mode: $mode (expected fs or image)" >&2
    exit 2
    ;;
esac
