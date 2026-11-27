# Feature Library

## Overview
Provides the library user interface, book discovery, Obsidian vault folder onboarding, metadata extraction, book cover caching, and sharing capabilities for KReader.

## Interface
- `LibraryScreen`: Top-level Composable displaying the active vault books and comics, responsive single-line search query bar with ellipsis overflow protection, format filter chips (`All` / `Books` / `Comics`), compact action icons, sort menu, file import FAB, and vault selection button (`[LibraryScreen.kt:LibraryScreen]`).
- `LibraryViewModel`: Presentation state holder coordinating vault file observation, CBZ subdirectory series grouping (`CbzSeries`), custom `cover.*` resolution, background metadata parsing, format filtering, and book/series imports (`[LibraryViewModel.kt:LibraryViewModel]`).
- `LibraryUiState`: Immutable state model representing standalone books, grouped CBZ series (`LibraryItem`), format filter (`BookFilter`), active series selection, search queries, and loading states (`[LibraryState.kt:LibraryUiState]`).
- `BookGrid`: Multiplatform grid composable rendering standalone book/comic cards (`BookItemCard`) and grouped CBZ series cards (`SeriesItemCard`) with format/chapter badges, reading progress, context menus, and multi-selection mode (`[BookGrid.kt:BookGrid]`).
- `SeriesChaptersSheet`: Material 3 bottom sheet displaying natural-sorted CBZ series chapters, per-chapter progress (`Page X / Y` / `Completed`), and a `Continue Reading` action (`[SeriesChaptersSheet.kt:SeriesChaptersSheet]`).
- `BookMetadataExtractor`: Platform-specific EPUB and CBZ (`ComicInfo.xml` + first-page cover) metadata extractor interface (`[BookMetadataExtractor.kt:BookMetadataExtractor]`).
- `PlatformFilePicker`: Platform-specific SAF folder and file picker launchers accepting `.epub` and `.cbz` files (`[PlatformFilePicker.kt:rememberFolderPickerLauncher]`).
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
- `[BookMetadataExtractor.android.kt]`: Reads EPUB zip streams (OPF metadata) and CBZ archives (`ComicInfo.xml` + first sorted image cover) to write cover thumbnails to `.kreader/covers/`.

## Verification
- Run: `./gradlew :feature:library:assemble`
