#!/usr/bin/env bash
# Builds the Arch Linux .pkg.tar.zst package from pre-compiled Gradle output
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"

echo "==> Staging Arch Linux packaging sources in $BUILD_DIR..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/lib"

cp "$SCRIPT_DIR/PKGBUILD" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.sh" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.desktop" "$BUILD_DIR/"
cp "$SCRIPT_DIR/kreader.svg" "$BUILD_DIR/"

echo "==> Locating compiled JARs from Gradle build..."
JAR_COUNT=0

# Check Compose Desktop output locations:
# In Compose Desktop createDistributable, JARs are placed in:
# composeApp/build/compose/binaries/main/app/kreader/lib/app/*.jar OR lib/*.jar
SEARCH_DIRS=(
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib/app"
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib"
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app"
    "$ROOT_DIR/composeApp/build/compose/binaries/main"
)

for dir in "${SEARCH_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "--> Checking directory: $dir"
        find "$dir" -maxdepth 2 -type f -name "*.jar" ! -path "*/runtime/*" -exec cp -v {} "$BUILD_DIR/lib/" \; 2>/dev/null || true
        JAR_COUNT=$(find "$BUILD_DIR/lib" -type f -name "*.jar" | wc -l)
        if [ "$JAR_COUNT" -gt 0 ]; then
            echo "--> Found and copied $JAR_COUNT JARs from $dir"
            break
        fi
    fi
done

# Fallback: search anywhere in composeApp/build for .jar files if still 0
if [ "$JAR_COUNT" -eq 0 ]; then
    echo "--> Performing broader search under $ROOT_DIR/composeApp/build..."
    find "$ROOT_DIR/composeApp/build" -type f -name "*.jar" ! -path "*/runtime/*" ! -path "*/tmp/*" ! -path "*/transformed/*" -exec cp -v {} "$BUILD_DIR/lib/" \; 2>/dev/null || true
    JAR_COUNT=$(find "$BUILD_DIR/lib" -type f -name "*.jar" | wc -l)
fi

echo "==> Total application and dependency JARs staged: $JAR_COUNT"
if [ "$JAR_COUNT" -eq 0 ]; then
    echo "ERROR: No JAR libraries found under $ROOT_DIR/composeApp/build." >&2
    echo "Ensure './gradlew :composeApp:createDistributable' was executed first." >&2
    exit 1
fi

cd "$BUILD_DIR"
echo "==> Running makepkg..."
makepkg --nodeps --noconfirm -f

echo "==> Generated packages:"
ls -lh "$BUILD_DIR"/*.pkg.tar.zst 2>/dev/null || true
