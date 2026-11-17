# KReader Linux Desktop Architecture & Arch Linux Packaging

* **Author:** Hysm25
* **Date:** 2026-11-17

## Overview

KReader Linux Desktop provides a native desktop reading experience for Arch Linux and CachyOS. It replaces the existing placeholder desktop implementations (`DesktopEpubReaderEngine` and `EpubNavigatorHost.desktop.kt`) with an in-house Compose Multiplatform EPUB layout engine and implements a native Arch Linux distribution pipeline (`.pkg.tar.zst` via `PKGBUILD`).

The desktop client integrates directly with KReader's local-first storage philosophy, sharing the exact `.kreader/` JSON vault schemas (`workspace.json`, `annotations.json`, `appearance.json`, `themes.json`, `covers/`, `fonts/`) utilized by Android. Reading progression, bookmarks, highlights, and custom appearance settings sync seamlessly across both platforms through portable text quote anchoring and deterministic Unicode normalization.

Packaging adheres to Arch Linux standards, installing application components into `/usr/lib/kreader/`, providing a launcher in `/usr/bin/kreader`, integrating desktop menu entries and icons, and relying on the host's system Java runtime (`depends=('java-runtime>=21')`) alongside required native graphical libraries to produce an ultra-lean (<20MB) package.

## Analysis

Seven specialized Goldfish review lenses evaluated this concept: `egm-design`, `egm-codebase-details`, `egm-zoom-out`, `egm-comprehension`, `egm-implementation-readiness`, `egm-structural`, and `egm-inventive-catalyst`. Their findings converged on critical architectural tensions and failure modes:

### 1. Document Architecture & The Pure Compose Parsing Trap (Contradiction & AST Resolution)
- **The False Compromise**: EPUB is structured around XHTML and CSS. Forcing arbitrary XHTML markup into a single Compose `AnnotatedString` is technically unworkable: `AnnotatedString` only models inline spans and character-level styling. It cannot represent block margins, blockquotes, floating images, tables, or column-based pagination. Attempting to parse and render arbitrary XHTML/CSS directly in Compose without an intermediate structure leads to a brittle, unmaintainable reinvention of a browser layout engine.
- **Inventive Resolution (Segmentation & Preliminary Action)**:
  - Deconstruct XHTML into a two-stage compilation pipeline:
    1. *Stage 1 (Tokenization & AST)*: Use `org.jsoup:jsoup` to parse XHTML into a clean, typed Abstract Syntax Tree (`ReaderContentBlock`: `Paragraph`, `Heading`, `Blockquote`, `ListItem`, `ImageBlock`, `Divider`).
    2. *Stage 2 (Virtualized Compose Layout)*: Render blocks inside virtualized Compose containers (`LazyColumn` for scroll mode, or virtualized page containers for pagination mode).
  - Use `TextMeasurer` / `MultiParagraph` to pre-calculate block layout bounds and line-break split points asynchronously on background threads (`Dispatchers.Default`), caching layout geometry to guarantee instant, stutter-free page transitions and smooth scrolling.
- **Secondary Trade-Offs**: Memory allocation for cached block layout measurements during reading, and slight initial parse overhead when opening dense chapters.

### 2. Cross-Platform Reading Position & Highlight Anchor Parity (Intermediary & Inversion)
- **The Divergence Hazard**: Android KReader calculates reading progression and highlights inside Readium's browser DOM tree. Desktop Compose measures text layout using Skia and HarfBuzz. If progression percentages or character offsets are calculated differently, highlights created on Android will drift, fail to match, or attach to the wrong text on Linux, and vice-versa.
- **Inventive Resolution (Canonical Text Spine Normalizer)**:
  - Synchronized navigation must anchor to chapter resource identifier (`locator.href`) plus a resilient block/element anchor or chapter-relative normalized character progression, not raw viewport percentage.
  - Standardize text extraction across both engines: prior to matching or generating `TextQuoteSelector` (`exact`, `prefix`, `suffix`), text must undergo deterministic Unicode NFC normalization and whitespace collapsing (converting all contiguous whitespace `[\r\n\t ]+` into a single space).
  - Implement a sliding window fuzzy match fallback with token distance tolerance to gracefully anchor highlights even when minor rendering variations occur between Readium and Compose.
  - The reader must never delete or prune existing annotations simply because they could not be matched in the current active viewport.

### 3. Desktop Filesystem Conventions & Multiplatform Font Paths
- **XDG Base Directory Compliance**: Desktop configuration and state must respect the Linux XDG specification:
  - Configuration: `$XDG_CONFIG_HOME/kreader` (`~/.config/kreader/`)
  - Application State & Logs: `$XDG_DATA_HOME/kreader` (`~/.local/share/kreader/`)
  - Cache: `$XDG_CACHE_HOME/kreader` (`~/.cache/kreader/`)
  - Vault Location: User-selected portable directory containing the `.kreader/` metadata folder.
- **Relative Font Path Serialization**: `CustomFont` currently serializes absolute paths (`path: String`). Absolute Linux paths (`/home/...`) break on Android, and Android `content://` URIs break on Linux. The schema must store relative font names within the vault (e.g., `fonts/Amiri.ttf`), allowing each platform adapter to resolve local file handles at runtime.

### 4. Arch Linux System Java Runtime & Graphics Driver Resilience
- **The Headless & Missing Dependency Trap**: Specifying `depends=('java-runtime>=21')` alone creates fatal runtime bugs:
  1. On Arch Linux, `java-runtime>=21` is satisfied by `jre21-openjdk-headless`, which crashes immediately on launch with `java.awt.HeadlessException`.
  2. Compose Desktop uses Skiko native binaries (`libskiko-linux-x64.so`), which require native graphical and font rendering libraries (`libx11`, `libxext`, `libxrender`, `libglvnd`, `fontconfig`, `freetype2`).
- **Launcher Cushioning & PKGBUILD Hardening**:
  - Update PKGBUILD dependencies to explicitly declare graphical runtime libraries:
    `depends=('java-runtime>=21' 'libglvnd' 'fontconfig' 'freetype2' 'libx11' 'libxext' 'libxrender' 'libxcursor')`
  - The `/usr/bin/kreader` launcher script must perform pre-flight verification: test for a non-headless JRE, verify graphical display environment (`$DISPLAY` or `$WAYLAND_DISPLAY`), detect Wayland vs X11 session type (`$XDG_SESSION_TYPE`) to pass optimal Skiko flags, and log startup errors to `~/.local/share/kreader/logs/startup.log`.

### 5. Atomic Vault I/O & Concurrency Protection
- Current desktop vault writes use direct `writeText(content)`. In multi-device setups using background sync tools (Syncthing, Nextcloud), a concurrent sync pass during a debounce write can result in truncated or zero-byte JSON files.
- All vault configuration updates must use atomic write-and-replace: write to `filename.json.tmp` and replace via `StandardCopyOption.ATOMIC_MOVE`. Maintain a backup `filename.json.bak` to recover from unexpected disk interruptions.

### 6. Desktop Interaction Model & Arabic RTL Navigation
- **Keyboard & Mouse Ergonomics**: Implement full keyboard shortcuts (`Space`/`PageDown`/`PageUp`/arrows for navigation, `Ctrl+F` for in-book search, `Ctrl+=`/`Ctrl+-`/`Ctrl+0` for font scaling, `Esc` to close overlays) and smooth mouse wheel interaction.
- **RTL Arabic Alignment**: Mirror arrow key navigation for RTL books (Left Arrow advances page; Right Arrow goes back). Normalize Arabic text search (strip tashkeel/diacritics and normalize alif/yaa variants) so in-book search achieves full recall.

### 7. Dual-Stage CI Packaging Pipeline
- Running `makepkg` directly inside an Ubuntu runner fails because Ubuntu lacks Arch packaging tools. Running `makepkg` as root inside an Arch container fails because `makepkg` explicitly blocks root execution.
- Establish a clean two-stage GitHub Actions job:
  1. *Stage 1 (Ubuntu Host)*: Gradle compiles Desktop application JARs and assets via `:composeApp:packageDistributionForCurrentOS` or a dedicated distribution task.
  2. *Stage 2 (Arch Linux Container)*: Pass the compiled archive into an `archlinux:latest` container running an unprivileged `builder` user with `NOPASSWD: ALL` sudo permissions, executing `makepkg --syncdeps --noconfirm` to output `kreader-<version>-1-x86_64.pkg.tar.zst` alongside SHA256 checksums.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  - Jsoup-backed EPUB tokenization pipeline compiling XHTML into a typed `ReaderContentBlock` AST.
  - Pure Compose Multiplatform reader UI supporting continuous vertical scrolling and single/dual-page virtual pagination.
  - Asynchronous block layout measurement and caching via `TextMeasurer` on background coroutines.
  - Image decoding cache with memory bounds (`LruCache<String, ImageBitmap>`) for embedded EPUB graphics.
  - Readium-compatible text canonicalization (NFC normalization and whitespace collapsing) ensuring highlight and reading progression parity with Android.
  - Resilient highlight anchoring using `TextQuoteSelector` with fuzzy match fallback.
  - Desktop keyboard shortcuts, mouse wheel scrolling/page turning, and drag-to-highlight interaction.
  - Arabic RTL layout direction, spatial navigation mirroring, and diacritic-insensitive search.
  - Linux CLI argument handling in `main(args: Array<String>)` to support opening EPUB files from desktop file managers.
  - Arch Linux package distribution: `PKGBUILD`, launcher script with pre-flight checks, desktop entry, scalable icon, and native dependencies.
  - Dual-stage GitHub Actions CI workflow producing verified `.pkg.tar.zst` release artifacts alongside Android APKs.

* **Out-of-Scope Non-Goals:**
  - Embedded browser engine runtimes (JavaFX WebView, CEF, WebKitGTK).
  - Full arbitrary CSS styling engine (floats, multi-level nested tables, CSS animations, JavaScript execution inside books).
  - Non-EPUB document formats (PDF, CBZ, CBR, MOBI).
  - Cloud server sync protocols (all data synchronization remains local-first via the filesystem vault).
  - Flatpak, Snap, AppImage, DEB, or RPM packaging in this pipeline.

## Alternatives Weighed

- *JavaFX WebView / SwingPanel*: Rejected due to high RAM overhead (>150MB baseline), fragile native linking against `libjfxwebkit.so`, and frequent Wayland compositor display glitches on Arch/CachyOS.
- *Single Monolithic AnnotatedString Rendering*: Rejected because Compose `AnnotatedString` cannot model block-level layout, margins, embedded chapter images, or multi-column pagination, which would severely degrade book layout fidelity.
- *Bundled JRE Distribution (jlink via createDistributable)*: Rejected by author in favor of system `java-runtime>=21` dependency to keep package size under 20MB and avoid redundant JVM runtime installations on Arch Linux.
- *Direct Source Build in Arch Linux Container*: Rejected due to excessive build times and network timeouts when downloading Android SDK and Gradle plugins inside an ephemeral container. Pre-compiling on Ubuntu host and packaging via makepkg in container provides optimal build reliability.

## Key Decisions & Staging for Next Steps

1. **Intermediate Representation**: Implement a typed `ReaderContentBlock` AST parsed via `org.jsoup:jsoup` rather than direct XHTML-to-string mapping.
2. **Text Normalization Standard**: Enforce shared Unicode NFC normalization and whitespace collapsing in `:core:engine-api` for all locator and quote computations.
3. **Packaging Architecture**: Define the `PKGBUILD` to consume pre-built Gradle distributable artifacts, target `/usr/lib/kreader/`, and enforce GUI runtime dependencies.
4. **Next Step**: Review the brief and proceed to `/egm-draft` to generate the formal architecture and implementation document.
