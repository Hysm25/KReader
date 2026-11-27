# KReader Blueprint

[![Android CI & Release](https://github.com/Hysm25/KReader/actions/workflows/android-dev.yml/badge.svg)](https://github.com/Hysm25/KReader/actions/workflows/android-dev.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.7.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Readium](https://img.shields.io/badge/Readium_Kotlin_Toolkit-3.0.0-0284C7.svg)](https://readium.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## System Overview

**KReader** is a modern, local-first EPUB reader for **Android** and **Desktop** built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

Built on an **Obsidian-style folder vault** philosophy, KReader allows you to open and manage your books directly inside any folder on your device or storage. Books remain in place—never duplicated into proprietary app databases or hidden sandboxes. All reading progress, preferences, and annotations are saved in clean, human-readable JSON files inside a portable `.kreader/` configuration directory.

```
KReader/
├── composeApp/                 # Application entry points (Android MainActivity & Desktop main), packaging DSL
├── core/
│   ├── model/                  # Domain entities (Book, Locator, ReaderPreferences, ReaderTheme)
│   ├── database/               # VaultStorage (.kreader JSON engine) & BookRepository
│   └── engine-api/             # Reader engine contracts, navigation controller & key dispatcher
├── engine/
│   └── epub-adapter/           # Readium 3.0 (Android) & Pure Compose Block AST engine (Desktop)
│   └── cbz-adapter/            # Native ZipFile CBZ archive engine, ComicInfo.xml parser, LRU bitmap preloader
└── feature/
    ├── library/                # Library screen, vault onboarding, book grid, metadata extraction
    └── reader/                 # Reader screen, ReaderViewModel, navigator hosts (Android Readium & Desktop AST)
```

---

## Subdirectories & System Boundaries

- **`composeApp`**: [./composeApp/README.md](./composeApp/README.md) - Application entry points (Android `MainActivity` and Desktop `main`), root Compose UI scaffolding (`App.kt`), desktop distribution tasks, and system lifecycle bridges.
- **`core`**: [./core/README.md](./core/README.md) - Foundational domain entities (`model`), local-first `.kreader/` JSON vault persistence (`database`), and reader engine contracts (`engine-api`).
- **`engine`**: [./engine/README.md](./engine/README.md) - Readium 3.0 EPUB publication engine adapter (Android production), pure Compose Block AST EPUB engine (Desktop production) (`epub-adapter`), and native `ZipFile` CBZ comic/manga archive & bitmap engine (`cbz-adapter`).
- **`feature`**: [./feature/README.md](./feature/README.md) - Presentation UI modules for book vault library browsing (`library`) and EPUB reader viewport controls (`reader`).

---

## Global Invariants

- **`[VaultWorkspace]`**: All reading progression, last read timestamps, and locator pointers are indexed strictly by relative vault paths (`relativePath`), ensuring full vault portability across local file systems, SD cards, and cloud drives.
- **`[Cross-Platform Locators]`**: Desktop and Android synchronize highlights and reading head positions through normalized `TextQuoteSelector` (`exact`, `prefix`, `suffix`) records with Unicode NFC canonicalization and whitespace collapsing.
- **`[Storage Access Framework & Atomic Writes]`**: Android vault operations preserve original files in-place without copying books to internal app sandbox storage; concurrent writes to `.kreader/*.json` configs are serialized via atomic replacement (`.tmp` -> `ATOMIC_MOVE`) to prevent data corruption.
- **`[Reader Viewport Stability]`**: Reading layouts enforce virtualized block rendering and floating overlay toolbars to eliminate viewport resize reflow and multi-column text overlap during theme or margin adjustments.

---

## Global Side Effects

- **`[Vault Directory Mutation]`**: Scans, creates, and writes configuration files (`workspace.json`, `appearance.json`, `annotations.json`, `themes.json`), cover images (`.kreader/covers/`), custom fonts (`.kreader/fonts/`), and exported notes (`<Book_Title>.md`) inside the user's selected vault directory.
- **`[Desktop Launcher Pre-flight Diagnostics]`**: The `/usr/bin/kreader` launcher checks for non-headless Java 21+ environments and graphical drivers, directing failure logs to `~/.local/share/kreader/logs/startup.log`.

---

## System Verification & Build Commands

All shell commands use `fish` syntax:

```fish
# Check project setup and validate tasks
./gradlew tasks

# Assemble the Android Debug APK locally
./gradlew :composeApp:assembleDebug --configuration-cache

# Run Desktop application
./gradlew :composeApp:run

# Package Desktop distribution locally
./gradlew :composeApp:createDistributable
```

---

## Key Features

### 📂 Obsidian-Style Folder Vault
- **In-Place EPUB Reading**: Select your books directory via Android Storage Access Framework (`OpenDocumentTree`) or Desktop file chooser. KReader scans and opens books in place.
- **Portable `.kreader/` Configuration**: All app data travels with your folder:
  - `.kreader/appearance.json`: Theme, typography, line height, padding, and reading mode settings.
  - `.kreader/workspace.json`: Reading progress percentages, last-opened timestamps, and serialized Readium `Locator` records keyed by portable relative file paths.
  - `.kreader/annotations.json`: Multi-color highlights and bookmarks keyed by book relative paths.
  - `.kreader/covers/`: Extracted cover art thumbnails cached directly inside the vault.
  - `.kreader/fonts/`: Imported `.ttf` and `.otf` custom fonts stored inside the vault.
- **Atomic Storage**: Thread-safe operations protected by atomic file updates, preventing corrupted or duplicate JSON files.

### 📖 High-Performance EPUB Engines
- **Android Engine (Readium 3.0)**: Powered by `readium-shared`, `readium-streamer`, and `readium-navigator` on Android.
- **Desktop Engine (Pure Compose Block AST)**: In-house Jsoup-based XHTML/CSS parser converting chapters into a typed `ReaderContentBlock` AST rendered natively in Compose with zero browser engine dependencies.
- **Automatic Reading Direction (RTL / LTR)**: Detects reading direction automatically from EPUB metadata or Arabic script detection. Navigation and keyboard arrow keys mirror automatically.
- **Dual-Page & Continuous Scroll Modes**: Seamlessly toggle between responsive continuous vertical scrolling and single or dual-column pagination with pre-calculated `TextMeasurer` layout bounds.

### 🎨 Custom Fonts & Typography
- **Custom Font Injection**: Import `.ttf` or `.otf` custom fonts into the vault. Desktop Compose routes fonts through Skia and HarfBuzz for Arabic shaping.
- **Live Typography Controls**: Real-time adjustments for font size, line height multiplier, and viewport margins.

### 🔍 In-Book Search & Annotations
- **In-Book Search**: Full-text search with match counts, keyword navigation, excerpt previews, and streaming coroutine execution.
- **Table of Contents**: Nested chapter navigation drawer with reading order mapping.
- **Cross-Platform Highlights & Notes**: Highlights synchronized across Android and Linux via W3C `TextQuoteSelector` (`exact`, `prefix`, `suffix`) matching.
