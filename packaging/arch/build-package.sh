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

echo "==> Locating compiled JARs and native libraries from Gradle build..."
JAR_COUNT=0

# Primary Compose Desktop application distribution directory
COMPOSE_APP_DIR="$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib/app"
if [ ! -d "$COMPOSE_APP_DIR" ]; then
    COMPOSE_APP_DIR="$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib"
fi

if [ -d "$COMPOSE_APP_DIR" ]; then
    echo "--> Staging desktop application and dependency JARs from: $COMPOSE_APP_DIR"
    find "$COMPOSE_APP_DIR" -maxdepth 1 -type f -name "*.jar" -exec cp -f {} "$BUILD_DIR/lib/" \;
else
    # Fallback search if createDistributable directory differs
    echo "--> Performing fallback search for Compose Desktop JARs..."
    find "$ROOT_DIR/composeApp/build/compose/binaries/main" -maxdepth 4 -type f -name "*.jar" ! -path "*/runtime/*" -exec cp -f {} "$BUILD_DIR/lib/" \; 2>/dev/null || true
fi

JAR_COUNT=$(find "$BUILD_DIR/lib" -type f -name "*.jar" | wc -l)
echo "==> Total application and dependency JARs staged: $JAR_COUNT"

if [ "$JAR_COUNT" -eq 0 ]; then
    echo "ERROR: No JAR libraries found under Compose Desktop output." >&2
    echo "Ensure './gradlew :composeApp:createDistributable' was executed first." >&2
    exit 1
fi

# Locate and stage only the x86_64 Skiko native runtime (.so)
echo "==> Staging Linux x64 Skiko native library..."
SKIKO_SO=$(find "$COMPOSE_APP_DIR" -maxdepth 1 -type f -name "libskiko-linux-x64.so" 2>/dev/null | head -n 1)
if [ -z "$SKIKO_SO" ]; then
    SKIKO_SO=$(find "$ROOT_DIR/composeApp/build" -type f -name "libskiko-linux-x64.so" ! -path "*/runtime/*" 2>/dev/null | head -n 1)
fi

if [ -n "$SKIKO_SO" ] && [ -f "$SKIKO_SO" ]; then
    cp -f "$SKIKO_SO" "$BUILD_DIR/lib/libskiko-linux-x64.so"
fi

# If Skiko native runtime JAR is present, extract native binaries if .so was not already found
SKIKO_JAR=$(find "$BUILD_DIR/lib" -name "*skiko-awt-runtime-linux-x64*.jar" 2>/dev/null | head -n 1)
if [ ! -f "$BUILD_DIR/lib/libskiko-linux-x64.so" ] && [ -n "$SKIKO_JAR" ] && [ -f "$SKIKO_JAR" ]; then
    echo "==> Extracting native binaries (*.so, *.sha256) from Skiko runtime JAR..."
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$SKIKO_JAR" "libskiko-linux-x64.so" "libskiko-linux-x64.so.sha256" -d "$BUILD_DIR/lib/" 2>/dev/null || true
    fi
fi

# Strip debug symbols from native library to reduce package bloat
if [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so" ]; then
    if command -v strip >/dev/null 2>&1; then
        echo "==> Stripping debug symbols from libskiko-linux-x64.so..."
        strip --strip-unneeded "$BUILD_DIR/lib/libskiko-linux-x64.so" 2>/dev/null || true
    fi
    # Generate canonical sha256 checksum matching the staged binary
    echo "==> Generating canonical libskiko-linux-x64.so.sha256 checksum..."
    sha256sum "$BUILD_DIR/lib/libskiko-linux-x64.so" | awk '{print $1}' | tr -d '\n' > "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256"
fi

# Inject ONLY the 64-byte sha256 resource into Skiko/Compose JARs for classloader verification
if command -v python3 >/dev/null 2>&1; then
    python3 -c "
import glob, os, zipfile

build_lib = '$BUILD_DIR/lib'
sha_file = os.path.join(build_lib, 'libskiko-linux-x64.so.sha256')

if os.path.exists(sha_file):
    for jar in glob.glob(os.path.join(build_lib, '*.jar')):
        bname = os.path.basename(jar).lower()
        if 'skiko' in bname or 'composeapp' in bname:
            print(f'--> Injecting Skiko sha256 checksum into {os.path.basename(jar)}')
            with zipfile.ZipFile(jar, 'a') as zf:
                zf.write(sha_file, 'libskiko-linux-x64.so.sha256')
"
fi

if [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so" ] && [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256" ]; then
    echo "==> Lean Skiko native runtime staged in $BUILD_DIR/lib/:"
    echo "    - libskiko-linux-x64.so ($(ls -lh "$BUILD_DIR/lib/libskiko-linux-x64.so" | awk '{print $5}'))"
    echo "    - libskiko-linux-x64.so.sha256 ($(cat "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256"))"
else
    echo "WARNING: libskiko-linux-x64.so or libskiko-linux-x64.so.sha256 not found in $BUILD_DIR/lib/!" >&2
fi

cd "$BUILD_DIR"
echo "==> Running makepkg with optimized zstd level 19 compression..."
export PKGEXT='.pkg.tar.zst'
export COMPRESSZST=(zstd -c -z -q -19 -T0 -)
makepkg --nodeps --noconfirm -f

echo "==> Generated packages:"
ls -lh "$BUILD_DIR"/*.pkg.tar.zst 2>/dev/null || true
