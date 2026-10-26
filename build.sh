#!/usr/bin/env bash
# Builds apk/no-default-browser.apk in Docker (nothing but docker needed on the host).
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=no-default-browser-builder

docker build -t "$IMAGE" .
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD:/src" "$IMAGE"

ls -lh apk/*.apk
