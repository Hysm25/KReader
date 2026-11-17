#!/usr/bin/env bash
# Builds the Arch Linux .pkg.tar.zst package from pre-compiled Gradle output
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"

echo "==> Staging Arch Linux packaging sources in $BUILD_DIR..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

cp "$SCRIPT_DIR/PKGBUILD" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.sh" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.desktop" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.svg" "$BUILD_DIR/"

# Copy precompiled libraries from Compose distributable output
COMPOSE_LIB_DIR=$(find "$ROOT_DIR/composeApp/build/compose/binaries/main" -type d -name "lib" 2>/dev/null | head -n 1)
if [ -n "$COMPOSE_LIB_DIR" ] && [ -d "$COMPOSE_LIB_DIR" ]; then
    echo "==> Bundling application JARs from $COMPOSE_LIB_DIR..."
    mkdir -p "$BUILD_DIR/lib"
    cp -a "$COMPOSE_LIB_DIR/"* "$BUILD_DIR/lib/"
else
    echo "WARNING: Precompiled lib directory not found under $ROOT_DIR/composeApp/build/compose/binaries/main." >&2
    echo "Ensure './gradlew :composeApp:createDistributable' was executed first." >&2
fi

cd "$BUILD_DIR"
echo "==> Running makepkg..."
makepkg --nodeps --noconfirm -f

echo "==> Generated packages:"
ls -lh "$BUILD_DIR"/*.pkg.tar.zst 2>/dev/null || true
