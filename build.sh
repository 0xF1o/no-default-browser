#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_IMAGE_NAME="android-browser-picker-builder"

# Clean up any existing build artifacts
rm -f "$SCRIPT_DIR"/apk/*.apk "$SCRIPT_DIR"/apk/*.tar 2>/dev/null || true
[ -d "$SCRIPT_DIR/apk" ] || mkdir -p "$SCRIPT_DIR/apk"

# Build the builder Docker image
echo "=== Building builder Docker image ==="
docker build -f "$SCRIPT_DIR/Dockerfile.build" -t "$BUILD_IMAGE_NAME" "$SCRIPT_DIR" --no-cache

# Create a container and run the build inside it
echo "=== Running build inside container ==="
CONTAINER_NAME="bp-build-$$"
docker create --name "$CONTAINER_NAME" "$BUILD_IMAGE_NAME" sleep 3600

# Copy the tar out of the container
echo "=== Extracting APKs ==="
docker cp "$CONTAINER_NAME:/apk-out.tar" "$SCRIPT_DIR/apk/apk-out.tar"
docker rm "$CONTAINER_NAME"

# Extract APKs from the tar into a staging dir
cd "$SCRIPT_DIR/apk"
mkdir -p _staging && tar -xf apk-out.tar -C _staging

# Copy APKs with variant names
for variant in release debug; do
  srcdir="_staging/$variant"
  [ -d "$srcdir" ] || continue
  for apk in "$srcdir"/*.apk; do
    [ -f "$apk" ] || continue
    cp "$apk" "${variant}-$(basename "$apk")"
  done
done
rm -rf _staging
rm -f apk-out.tar

echo "=== Done: APKs in apk/ ==="
ls -lh "$SCRIPT_DIR"/apk/*.apk 2>/dev/null || echo "(no APKs found)"
