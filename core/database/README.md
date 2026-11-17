# Core Database

## Overview
Provides the local-first storage engine and repository layer for KReader. Implements the Obsidian-style folder vault philosophy, persisting reading progression, bookmarks, highlights, typography preferences, and custom themes inside human-readable JSON files within the `.kreader/` vault directory.

## Interface
- `BookRepository`: Core repository interface exposing reactive flows and asynchronous mutations for books, reading progression, annotations, preferences, and markdown exports (`[BookRepository.kt:BookRepository]`).
- `VaultBookRepository`: Concrete repository implementation coordinating `VaultStorage` and JSON serialization (`[BookRepository.kt:VaultBookRepository]`).
- `VaultStorage`: Platform-specific file system and Storage Access Framework (SAF) abstraction (`[VaultStorage.kt:VaultStorage]`).
- `VaultWorkspace`: Data contract for `.kreader/workspace.json` storing per-book reading progression records (`[VaultModels.kt:VaultWorkspace]`).
- `VaultAnnotations`: Data contract for `.kreader/annotations.json` storing bookmarks and highlights (`[VaultModels.kt:VaultAnnotations]`).
- `VaultThemes`: Data contract for `.kreader/themes.json` storing custom color themes (`[VaultModels.kt:VaultThemes]`).
- `convertHtmlToMarkdown`: Utility extracting structured Markdown from EPUB chapter HTML/XHTML documents (`[EpubToMarkdown.kt:convertHtmlToMarkdown]`).

## Invariants
- `[VaultStorage.android.kt:writeConfig]`: Concurrent writes to vault configuration files (`workspace.json`, `appearance.json`, `annotations.json`, `themes.json`) are guarded by per-filename `Mutex` locks in a `ConcurrentHashMap`.
- `[VaultStorage.android.kt:writeConfig]`: Reentrant or duplicate stray files matching pattern `<name> (\d+).json` created by Android SAF are detected and deleted prior to writing.
- `[VaultModels.kt:VaultWorkspace]`: Books are keyed in `VaultWorkspace` by their relative vault paths, ensuring vault portability across directories and devices.
- `[BookRepository.kt:exportBookToMarkdown]`: Book titles are sanitized against regex `[^a-zA-Z0-9_ \u0600-\u06FF-]` before creating markdown export files in the vault.

## Side Effects
- `[VaultStorage.android.kt:writeConfig]`: Writes UTF-8 JSON payloads directly to Android `ContentResolver` streams using `"wt"` truncate mode.
- `[VaultStorage.android.kt:registerVaultContentObserver]`: Registers and unregisters Android system `ContentObserver` listening for external changes to the document tree URI.
- `[VaultStorage.desktop.kt:setActiveVault]`: Persists the active vault path to Java `Preferences.userRoot().node("io.github.kreader.vault")`.
- `[VaultStorage.kt:saveCover, saveFont]`: Creates subdirectories `.kreader/covers` and `.kreader/fonts` and writes binary files to local storage.

## Verification
- Run: `./gradlew :core:database:assemble`
