# Core Model

## Overview
Defines core domain entities, value objects, reading preference models, and locator definitions for the KReader application. This package provides platform-agnostic, serializable representations of books, bookmarks, highlights, reading progress, themes, and typography settings without dependencies on storage or UI frameworks.

## Interface
- `Book`: Core data class representing a publication with metadata, format, source, progression, and locator references (`[Book.kt:Book]`).
- `BookFormat`: Publication format enumeration supporting EPUB with MIME type and extension resolution (`[BookFormat.kt:BookFormat]`).
- `BookSource`: Sealed interface abstracting storage origins (`FilePath`, `ContentUri`, `InactiveSource`) (`[BookSource.kt:BookSource]`).
- `Bookmark`: User bookmark anchor associating a publication with a locator and timestamp (`[Bookmark.kt:Bookmark]`).
- `Highlight`: User highlight annotation associating text ranges, colors, notes, and timestamps (`[Highlight.kt:Highlight]`).
- `Locator`: Readium-compliant location pointer referencing publication resource URLs, progression percentages, and text excerpts (`[Locator.kt:Locator]`).
- `ReaderPreferences`: Aggregate container for theme, typography, layout, brightness, volume key navigation, and orientation preferences (`[ReaderPreferences.kt:ReaderPreferences]`).
- `ReaderTheme`: Visual appearance configuration supporting light, dark, sepia, amoled, system, and custom palettes (`[ReaderTheme.kt:ReaderTheme]`).
- `ReaderTypography`: Font, font size, line height, letter spacing, alignment, and viewport padding configuration (`[ReaderTheme.kt:ReaderTypography]`).
- `ExternalBookOpener`: Reactive state holder for external EPUB open requests (`[ExternalBookOpener.kt:ExternalBookOpener]`).
- `isRtlText`: Detection utility checking for Arabic, Persian, Urdu, and Hebrew character ranges (`[TextDirectionUtils.kt:isRtlText]`).
- `isRtlLanguage`: Detection utility matching RTL language codes and names (`[TextDirectionUtils.kt:isRtlLanguage]`).

## Invariants
- `[BookFormat.kt:BookFormat.fromExtension]`: Extension lookup normalizes casing and strips leading periods before matching against supported extension lists.
- `[Locator.kt:Locator.initial]`: Initial locators must initialize both progression and totalProgression to `0f`.
- `[BookSource.kt:BookSource]`: Every BookSource implementation must provide a non-empty string identifier used for cross-session keying.
- `[ReaderPreferences.kt:ReaderPreferences]`: Default reader typography enforces single-column reading with a minimum horizontal padding of 16dp and line height multiplier of 1.6f.

## Side Effects
- `[ExternalBookOpener.kt:ExternalBookOpener.open]`: Mutates in-memory `_incomingSource` StateFlow triggering downstream observers in the UI navigation loop. No disk or network I/O.

## Verification
- Run: `./gradlew :core:model:assemble`
