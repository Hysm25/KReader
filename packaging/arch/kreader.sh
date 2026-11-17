#!/usr/bin/env bash
# KReader Desktop Launcher with Pre-flight Diagnostics
set -e

APP_NAME="kreader"
APP_DIR="/usr/lib/kreader"
LOG_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/kreader/logs"
STARTUP_LOG="$LOG_DIR/startup.log"

mkdir -p "$LOG_DIR"

log_diagnostic() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg" >> "$STARTUP_LOG"
}

# 1. Verify Java Executable Presence
if ! command -v java >/dev/null 2>&1; then
    log_diagnostic "Error: Java command not found in PATH."
    echo "Error: KReader requires Java 21 or higher, but no Java runtime was found in your PATH." >&2
    echo "Install a desktop Java 21 runtime with: sudo pacman -S jre-openjdk" >&2
    exit 1
fi

# 2. Verify Java Version >= 21
JAVA_VER_STRING=$(java -version 2>&1 | head -n 1)
JAVA_MAJOR_VER=$(echo "$JAVA_VER_STRING" | sed -E 's/.*version "([0-9]+).*/\1/')

if [[ "$JAVA_MAJOR_VER" =~ ^[0-9]+$ ]] && [ "$JAVA_MAJOR_VER" -lt 21 ]; then
    log_diagnostic "Error: Unsupported Java version ($JAVA_MAJOR_VER detected, >= 21 required)."
    echo "Error: KReader requires Java 21 or higher. Detected Java version: $JAVA_MAJOR_VER" >&2
    echo "Switch your active Java environment with: sudo archlinux-java set java-21-openjdk" >&2
    exit 1
fi

# 3. Verify Non-Headless Java Runtime
if java -XshowSettings:properties -version 2>&1 | grep -qi "headless.*true"; then
    log_diagnostic "Error: Headless Java runtime detected."
    echo "Error: KReader requires a graphical Java Runtime Environment (AWT/Swing)." >&2
    echo "Your active Java environment is headless (e.g., jre-openjdk-headless)." >&2
    echo "Install the full graphical runtime: sudo pacman -S jre-openjdk" >&2
    exit 1
fi

# 4. Verify Graphical Display Server Availability
if [ -z "$WAYLAND_DISPLAY" ] && [ -z "$DISPLAY" ]; then
    log_diagnostic "Error: No graphical display server found (WAYLAND_DISPLAY and DISPLAY are both unset)."
    echo "Error: KReader requires an active Wayland or X11 graphical display server." >&2
    exit 1
fi

# 5. Configure JVM and Rendering Options
JVM_OPTS=(
    "-Dfile.encoding=UTF-8"
    "-Dsun.java2d.uiScale.enabled=true"
    "-Dawt.useSystemAAFontSettings=on"
    "-Dswing.aatext=true"
)

# Optimize for Wayland compositors (CachyOS / Arch defaults)
if [ "$XDG_SESSION_TYPE" = "wayland" ]; then
    JVM_OPTS+=("-Dskiko.renderApi=OPENGL")
fi

# 6. Locate Classpath
CLASSPATH=""
if [ -d "$APP_DIR/lib" ]; then
    CLASSPATH="$APP_DIR/lib/*"
fi
if [ -d "$APP_DIR" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/*"
fi

if [ -z "$CLASSPATH" ] || [ -z "$(find -L "$APP_DIR" -name '*.jar' 2>/dev/null | head -n 1)" ]; then
    # Fallback to local execution directory if running from unpacked build
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if [ -d "$SCRIPT_DIR/../lib" ]; then
        CLASSPATH="$SCRIPT_DIR/../lib/*"
    elif [ -d "$SCRIPT_DIR/lib" ]; then
        CLASSPATH="$SCRIPT_DIR/lib/*"
    fi
fi

# 7. Launch KReader
log_diagnostic "Starting KReader with Java $JAVA_MAJOR_VER..."
log_diagnostic "Classpath: $CLASSPATH"
exec java "${JVM_OPTS[@]}" -cp "$CLASSPATH" io.github.kreader.MainKt "$@" 2> >(tee -a "$STARTUP_LOG" >&2)
