# Feature Library

## Overview
Provides the library user interface, book discovery, Obsidian vault folder onboarding, metadata extraction, book cover caching, and sharing capabilities for KReader.

## Interface
- `LibraryScreen`: Top-level Composable displaying the active vault books, responsive single-line search query bar with ellipsis overflow protection, compact action icons, sort menu, and vault selection button (`[LibraryScreen.kt:LibraryScreen]`).
- `LibraryViewModel`: Presentation state holder coordinating vault file observation, background metadata parsing, cover extraction, and book imports (`[LibraryViewModel.kt:LibraryViewModel]`).
- `LibraryUiState`: Immutable state model representing book lists, search filters, loading states, and active vault paths (`[LibraryState.kt:LibraryUiState]`).
- `BookGrid`: Multiplatform grid composable rendering book cover cards, reading badges, context menus, and multi-selection mode (`[BookGrid.kt:BookGrid]`).
- `BookMetadataExtractor`: Platform-specific EPUB metadata and cover image extractor interface (`[BookMetadataExtractor.kt:BookMetadataExtractor]`).
- `PlatformFilePicker`: Platform-specific SAF folder picker launcher (`[PlatformFilePicker.kt:rememberFolderPickerLauncher]`).
- `BookRepositoryProvider`: Platform factory providing the active `BookRepository` (`[BookRepositoryProvider.kt:rememberBookRepository]`).
- `CoverPainter`: Async cover image painter caching rendered thumbnails (`[CoverPainter.kt:rememberCoverPainter]`).
- `BookShareLauncher`: Platform intent dispatcher for sharing books (`[BookShareLauncher.kt:rememberBookShareLauncher]`).

## Invariants
- `[LibraryViewModel.kt:extractionSemaphore]`: Concurrent background metadata and cover extraction is bounded by a `Semaphore(3)` to prevent thermal throttling and memory spikes on mobile devices.
- `[LibraryViewModel.kt:extractingBookIds]`: Thread-safe synchronized set prevents duplicate parallel extraction passes on the same book.
- `[LibraryState.kt:LibraryUiState]`: Filtered books list is automatically updated whenever the underlying book set, search query, filter type, or sort order changes.

## Side Effects
- `[LibraryViewModel.kt:setVault]`: Requests storage access framework persistable permissions and saves the active vault URI/path in preferences.
- `[PlatformFilePicker.android.kt]`: Launches Android `OpenDocumentTree` intent and requests persistable read and write URI permissions.
- `[BookMetadataExtractor.android.kt]`: Reads EPUB zip streams to extract OPF metadata and writes cover thumbnails to `.kreader/covers/`.

## Verification
- Run: `./gradlew :feature:library:assemble`
