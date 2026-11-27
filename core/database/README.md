# Core Database

## Overview
Provides the local-first storage engine and repository layer for KReader. Implements the Obsidian-style folder vault philosophy, persisting reading progression, bookmarks, highlights, typography preferences, and custom themes inside human-readable JSON files within the `.kreader/` vault directory.

## Interface
- `BookRepository`: Core repository interface exposing reactive flows and asynchronous mutations for books, reading progression, annotations, preferences (including atomic `updateReaderPreferences`), and markdown exports (`[BookRepository.kt:BookRepository]`).
- `JsonVaultBookRepository`: Concrete repository implementation coordinating `VaultStorage` and JSON serialization (`[BookRepository.kt:JsonVaultBookRepository]`).
- `VaultStorage`: Platform-specific file system and Storage Access Framework (SAF) abstraction (`[VaultStorage.kt:VaultStorage]`).
- `VaultWorkspace`: Data contract for `.kreader/workspace.json` storing per-book reading progression records (`[VaultModels.kt:VaultWorkspace]`).
- `VaultAnnotations`: Data contract for `.kreader/annotations.json` storing bookmarks and highlights (`[VaultModels.kt:VaultAnnotations]`).
- `VaultThemes`: Data contract for `.kreader/themes.json` storing custom color themes (`[VaultModels.kt:VaultThemes]`).
- `convertHtmlToMarkdown`: Utility extracting structured Markdown from EPUB chapter HTML/XHTML documents (`[EpubToMarkdown.kt:convertHtmlToMarkdown]`).

## Invariants
- `[VaultStorage.android.kt:writeConfig]`: Concurrent writes to vault configuration files (`workspace.json`, `appearance.json`, `annotations.json`, `themes.json`) are guarded by per-filename `Mutex` locks in a `ConcurrentHashMap`. Config file Uris are cached in `configFileUriCache` to avoid expensive SAF directory traversal queries on every write, and internal `.kreader/` mutations suppress self-triggered `ContentObserver` vault rescans.
- `[BookRepository.kt:JsonVaultBookRepository]`: In-memory state mutations for `_workspace`, `_annotations`, and `_preferences` are serialized via `workspaceMutex`, `annotationsMutex`, and `prefsMutex`; direct `workspace.json` mutations (`updateBookPreferencesOverride`, `resetPreferences`, `deleteBook`) cancel any pending debounced `saveWorkspaceJob` so stale progress snapshots never overwrite overrides or deletions.
- `[VaultStorage.android.kt:scanDocumentTree]`: Recursively scans the active SAF tree for `.epub` and `.cbz` files (excluding `.kreader/`) and normalizes cover cache keys via `coverCacheBaseName` so both formats share identical `.kreader/covers/`, `workspace.json`, and `annotations.json` lifecycles.
- `[VaultModels.kt:VaultWorkspace]`: Books and comics are keyed in `VaultWorkspace` by their relative vault paths, ensuring vault portability across directories and devices. Per-book overrides use `ReaderPreferencesOverride` for sparse per-property inheritance (including CBZ `cbzReadingMode` and `cbzImageFit`) without freezing global preferences.
- `[BookRepository.kt:exportBookToMarkdown]`: Book titles are sanitized against regex `[^a-zA-Z0-9_ \u0600-\u06FF-]` before creating markdown export files in the vault.

## Side Effects
- `[VaultStorage.android.kt:writeConfig]`: Writes UTF-8 JSON payloads directly to Android `ContentResolver` streams using `"wt"` truncate mode.
- `[VaultStorage.android.kt:registerVaultContentObserver]`: Registers and unregisters Android system `ContentObserver` listening for external changes to the document tree URI.
- `[VaultStorage.desktop.kt:setActiveVault]`: Persists the active vault path to Java `Preferences.userRoot().node("io.github.kreader.vault")`.
- `[VaultStorage.kt:saveCover, saveFont]`: Creates subdirectories `.kreader/covers` and `.kreader/fonts` and writes binary files to local storage.

## Verification
- Run: `./gradlew :core:database:assemble`
