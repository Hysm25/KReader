# KReader

[![Android CI & Release](https://github.com/Hysm25/KReader/actions/workflows/android-dev.yml/badge.svg)](https://github.com/Hysm25/KReader/actions/workflows/android-dev.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Readium](https://img.shields.io/badge/Readium_Kotlin_Toolkit-3.0.0-0284C7.svg)](https://readium.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**KReader** is a modern, local-first EPUB reader for Android and Desktop built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

Built on an **Obsidian-style folder vault** philosophy, KReader allows you to open and manage your books directly inside any folder on your device or storage. Books remain in place—never duplicated into proprietary app databases or hidden sandboxes. All reading progress, preferences, and annotations are saved in clean, human-readable JSON files inside a portable `.kreader/` configuration directory.

---

## Key Features

### 📂 Obsidian-Style Folder Vault
- **In-Place EPUB Reading**: Select your books directory via Android Storage Access Framework (`OpenDocumentTree` with persistable permissions) or Desktop file chooser. KReader scans and opens books in place without copying them to internal app storage.
- **Portable `.kreader/` Configuration**: All app data travels with your folder:
  - `.kreader/appearance.json`: Theme, typography, line height, padding, and reading mode settings.
  - `.kreader/workspace.json`: Reading progress percentages, last-opened timestamps, and serialized Readium `Locator` records keyed by portable relative file paths.
  - `.kreader/annotations.json`: Multi-color highlights and bookmarks keyed by book relative paths.
  - `.kreader/covers/`: Extracted cover art thumbnails cached directly inside the vault for instant subsequent launches.
  - `.kreader/fonts/`: Imported `.ttf` and `.otf` custom fonts stored inside the vault.
- **Concurrency & Atomic Storage**: Thread-safe operations protected by per-file `Mutex` locks with atomic overwrites, preventing corrupted or duplicate `* (1).json` files.

### 📖 High-Performance EPUB Engine (Readium 3.0)
- **Readium Kotlin Toolkit 3.0**: Powered by `readium-shared`, `readium-streamer`, and `readium-navigator`.
- **Automatic Reading Direction (RTL / LTR)**: Detects reading direction automatically from EPUB metadata (`page-progression-direction="rtl"`, language tags, or Arabic script detection). The reader layout and bottom progress bar mirror automatically.
- **Single-Page Column Stability**: Enforced single-column layout (`ColumnCount.ONE`) paired with post-layout realignment prevents multi-column text overlap glitches when customizing margins.
- **Fixed Viewport (Zero Reflow)**: Floating overlay top and bottom bars ensure that toggling controls never resizes the reader container or reflows text.
- **Physical Volume Key Navigation**: Turn pages effortlessly with hardware volume buttons.

### 🎨 Instant Custom Fonts & Typography
- **Instant Font Application**: Import `.ttf` or `.otf` custom fonts. Fonts are pre-warmed on background threads and injected before initial paint, eliminating default-font flashes on chapter and page turns.
- **Live Typography Controls**: Real-time adjustments for font size, line height multiplier, and horizontal/vertical viewport padding without resetting reading position.

### ⚡ 60Hz / 120Hz High Refresh Rate
- Automatically requests the highest supported display refresh rate (60Hz, 90Hz, 120Hz) matching your device resolution.
- Full hardware acceleration enabled across both the application window and WebView renderer.

### 🔍 In-Book Search & Annotations
- **In-Book Search**: Fast full-text search with match counts, keyword navigation, excerpt previews, and empty-state guidance.
- **Table of Contents**: Nested chapter navigation drawer with reading order mapping.
- **Highlights & Notes**: Highlight text in multiple colors (Yellow, Green, Blue, Pink), attach notes, and manage bookmarks.

### 📱 Material 3 Expressive UI
- **Pure Color Schemes**: Thoughtfully designed palettes for **System** (synced with OS dark mode, matching EPUB page colors), **Light**, **Sepia** (warm paper), **Dark**, and **AMOLED** (true `#000000`).
- **Refined Color Tokens**: Eliminates default lavender/purple tints in favor of neutral slate and warm brown tones.
- **Library Grid**: Visual cover cards, progress badges, sort options (Recently Read, Recently Added, Title), multi-select batch sharing, and folder file deletion.

---

## Tech Stack

| Component | Technology |
|---|---|
| **Language** | [Kotlin 2.1.0](https://kotlinlang.org) |
| **UI Framework** | [Compose Multiplatform 1.7.1](https://www.jetbrains.com/lp/compose-multiplatform/) |
| **EPUB Engine** | [Readium Kotlin Toolkit 3.0.0](https://readium.org) (`streamer`, `navigator`, `shared`) |
| **Design System** | Material Design 3 (Compose Material 3, Material Icons Extended) |
| **Data Persistence** | `kotlinx.serialization.json` (Human-readable `.kreader` vault) |
| **Concurrency** | `kotlinx.coroutines` |
| **Date & Time** | `kotlinx.datetime` |
| **File I/O** | Android DocumentFile (SAF), Okio |
| **Target Platforms** | Android (minSdk 24, targetSdk 35) & Desktop (JVM 17) |

---

## Project Architecture

```
KReader/
├── composeApp/                 # Application entry points (Android MainActivity & Desktop main)
├── core/
│   ├── model/                  # Domain entities (Book, Locator, ReaderPreferences, ReaderTheme)
│   ├── database/               # VaultStorage (.kreader JSON engine) & BookRepository
│   └── engine-api/             # Reader engine contracts, navigation controller & key dispatcher
├── engine/
│   └── epub-adapter/           # Readium 3.0 integration, publication opener & locator mapping
├── feature/
│   ├── library/                # Library screen, vault onboarding, book grid, metadata extraction
│   └── reader/                 # Reader screen, Readium navigator host, typography sheets, search
├── gradle/                     # Version catalog (libs.versions.toml) and build configuration
└── .github/workflows/          # Automated GitHub Actions CI and release automation
```

---

## Installation

### Download APK
Pre-compiled APKs for Android devices are available on the [GitHub Releases](https://github.com/Hysm25/KReader/releases) page:
- **`arm64-v8a`**: Recommended for modern Android smartphones and tablets (64-bit ARM).
- **`armeabi-v7a`**: For older 32-bit ARM devices.
- **`x86_64`**: For Android emulators and x86-based Android devices.

---

## Building from Source

### Prerequisites
- **JDK 17** (Temurin recommended)
- **Android SDK** (API Level 35)

### Build Commands

Using standard terminal or `fish` shell:

```fish
# Check Gradle configuration and available tasks
./gradlew tasks

# Assemble Android Debug APKs (arm64-v8a, armeabi-v7a, x86_64)
./gradlew :composeApp:assembleDebug --configuration-cache

# Assemble Android Release APKs
./gradlew :composeApp:assembleRelease --configuration-cache

# Run Desktop Application (JVM)
./gradlew :composeApp:run
```

APKs will be located in:
- Debug: `composeApp/build/outputs/apk/debug/`
- Release: `composeApp/build/outputs/apk/release/`

---

## License

This project is licensed under the [MIT License](LICENSE).
