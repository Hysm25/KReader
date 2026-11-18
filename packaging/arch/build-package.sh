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

# Check Compose Desktop output locations:
# In Compose Desktop createDistributable, JARs are placed in:
# composeApp/build/compose/binaries/main/app/kreader/lib/app/*.jar OR lib/*.jar
SEARCH_DIRS=(
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib/app"
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app/kreader/lib"
    "$ROOT_DIR/composeApp/build/compose/binaries/main/app"
    "$ROOT_DIR/composeApp/build/compose/binaries/main"
    "$ROOT_DIR/composeApp/build/libs"
)

for dir in "${SEARCH_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "--> Scanning directory: $dir"
        find "$dir" -maxdepth 2 -type f -name "*.jar" ! -path "*/runtime/*" -exec cp -f {} "$BUILD_DIR/lib/" \; 2>/dev/null || true
    fi
done

JAR_COUNT=$(find "$BUILD_DIR/lib" -type f -name "*.jar" | wc -l)

# Fallback: search anywhere in composeApp/build for .jar files if still 0
if [ "$JAR_COUNT" -eq 0 ]; then
    echo "--> Performing broader search under $ROOT_DIR/composeApp/build..."
    find "$ROOT_DIR/composeApp/build" -type f -name "*.jar" ! -path "*/runtime/*" ! -path "*/tmp/*" ! -path "*/transformed/*" -exec cp -f {} "$BUILD_DIR/lib/" \; 2>/dev/null || true
    JAR_COUNT=$(find "$BUILD_DIR/lib" -type f -name "*.jar" | wc -l)
fi

echo "==> Total application and dependency JARs staged: $JAR_COUNT"
if [ "$JAR_COUNT" -eq 0 ]; then
    echo "ERROR: No JAR libraries found under $ROOT_DIR/composeApp/build." >&2
    echo "Ensure './gradlew :composeApp:createDistributable' was executed first." >&2
    exit 1
fi

# Copy any standalone native .so libraries from Compose build
echo "==> Scanning for native libraries (*.so)..."
find "$ROOT_DIR/composeApp/build" -type f -name "*.so" ! -path "*/runtime/*" ! -path "*/tmp/*" -exec cp -f -v {} "$BUILD_DIR/lib/" \; 2>/dev/null || true

# If Skiko native runtime JAR is present, unpack native binaries so they are available standalone
SKIKO_JAR=$(find "$BUILD_DIR/lib" -name "*skiko-awt-runtime-linux-x64*.jar" 2>/dev/null | head -n 1)
if [ -z "$SKIKO_JAR" ]; then
    SKIKO_JAR=$(find "$ROOT_DIR/composeApp/build" -name "*skiko-awt-runtime-linux-x64*.jar" 2>/dev/null | head -n 1)
    if [ -n "$SKIKO_JAR" ] && [ -f "$SKIKO_JAR" ]; then
        echo "--> Found Skiko JAR in build tree: $SKIKO_JAR"
        cp -f "$SKIKO_JAR" "$BUILD_DIR/lib/"
    fi
fi

if [ -n "$SKIKO_JAR" ] && [ -f "$SKIKO_JAR" ]; then
    echo "==> Found Skiko native runtime JAR: $(basename "$SKIKO_JAR")"
    echo "==> Extracting native binaries (*.so, *.sha256) from Skiko JAR..."
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$SKIKO_JAR" "*.so" "*.sha256" -d "$BUILD_DIR/lib/" 2>/dev/null || true
    fi
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "
import zipfile
with zipfile.ZipFile('$SKIKO_JAR', 'r') as z:
    for name in z.namelist():
        if name.endswith('.so') or name.endswith('.sha256'):
            z.extract(name, '$BUILD_DIR/lib/')
" 2>/dev/null || true
    fi
fi

# Ensure libskiko-linux-x64.so.sha256 is generated if libskiko-linux-x64.so is present
if [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so" ]; then
    echo "==> Ensuring libskiko-linux-x64.so.sha256 is present and valid..."
    sha256sum "$BUILD_DIR/lib/libskiko-linux-x64.so" | awk '{print $1}' | tr -d '\n' > "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256"
fi

# Inject Skiko native library and sha256 resource into JARs so getResourceAsStream finds them
if command -v python3 >/dev/null 2>&1; then
    python3 -c "
import glob, os, zipfile

build_lib = '$BUILD_DIR/lib'
so_file = os.path.join(build_lib, 'libskiko-linux-x64.so')
sha_file = os.path.join(build_lib, 'libskiko-linux-x64.so.sha256')

if os.path.exists(sha_file):
    for jar in glob.glob(os.path.join(build_lib, '*.jar')):
        bname = os.path.basename(jar).lower()
        if 'skiko' in bname or 'composeapp' in bname:
            print(f'--> Injecting Skiko runtime assets into {os.path.basename(jar)}')
            with zipfile.ZipFile(jar, 'a') as zf:
                zf.write(sha_file, 'libskiko-linux-x64.so.sha256')
                if os.path.exists(so_file) and 'skiko' in bname:
                    zf.write(so_file, 'libskiko-linux-x64.so')
" 2>/dev/null || true
fi

if [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so" ] && [ -f "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256" ]; then
    echo "==> Skiko native runtime verified in $BUILD_DIR/lib/:"
    echo "    - libskiko-linux-x64.so ($(ls -lh "$BUILD_DIR/lib/libskiko-linux-x64.so" | awk '{print $5}'))"
    echo "    - libskiko-linux-x64.so.sha256 ($(cat "$BUILD_DIR/lib/libskiko-linux-x64.so.sha256"))"
else
    echo "WARNING: libskiko-linux-x64.so or libskiko-linux-x64.so.sha256 not found in $BUILD_DIR/lib/!" >&2
fi

cd "$BUILD_DIR"
echo "==> Running makepkg..."
makepkg --nodeps --noconfirm -f

echo "==> Generated packages:"
ls -lh "$BUILD_DIR"/*.pkg.tar.zst 2>/dev/null || true
