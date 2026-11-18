# Feature Reader

## Overview
Provides the multiplatform EPUB reading viewport, Readium navigator hosting on Android, native Compose Block AST reader hosting on Desktop, floating controls overlay, typography and theme customization sheets, table of contents navigation, full-text search, and annotation management.

## Interface
- `ReaderViewModel`: Presentation state holder orchestrating reading sessions, bookmarks, highlights, notes, search queries, TOC, and sparse per-property typography/theme overrides (`[ReaderViewModel.kt:ReaderViewModel]`).
- `rememberReaderViewModel`: Composable factory hook creating and retaining the `ReaderViewModel` instance (`[ReaderViewModel.kt:rememberReaderViewModel]`).
- `ReaderScreen`: Top-level Composable scaffolding the reading container (with tablet responsive centering up to 840dp), floating top bar displaying book and current chapter title, bottom scrubber bar, settings sheets, search drawer, and note dialogs (`[ReaderScreen.kt:ReaderScreen]`).
- `ReaderUiState`: Immutable state model holding reading session data, controls visibility, drawer states, search results, themes, and current locators (`[ReaderUiState.kt:ReaderUiState]`).
- `EpubNavigatorHost`: Multiplatform Compose bridge hosting the platform EPUB navigator (`[EpubNavigatorHost.kt:EpubNavigatorHost]`).
  - **Android**: Embeds Readium's `EpubNavigatorFragment` inside an `AndroidView`, calculating robust book-wide total progression across spine items.
  - **Desktop (Linux/JVM)**: Embeds an in-house pure Compose Multiplatform reading viewport supporting continuous vertical scrolling, virtual single/dual-page pagination with `TextMeasurer`, `.kreader/` vault theme styling, custom font loading, native RTL/LTR Arabic BiDi rendering, highlight creation (`TextQuoteSelector`), TOC jumps, and in-book search.
- `EpubNavigatorController`: Imperative controller exposing programmatic navigation hooks (`goTo`, `goNext`, `goPrevious`, `seekToProgression`, `search`, `highlightSearchResult`) (`[EpubNavigatorController.kt:EpubNavigatorController]`).
- `ReaderSettingsSheet`: Bottom sheet modal with curated built-in theme presets (System, Light, Sepia, Dark, AMOLED), typography adjustments, scroll mode, and orientation locks (`[ReaderSheets.kt:ReaderSettingsSheet]`).
- `ReaderControls`: Floating top and bottom reading bars with chapter title display, smooth drag progress scrubber, and chapter jump controls (`[ReaderControls.kt:ReaderControls]`).
- `ManageSystemBars`: Platform effect managing edge-to-edge immersive sticky fullscreen (`[ManageSystemBars.kt:ManageSystemBars]`).
- `ReaderPlatformEffects`: Platform effect applying display brightness and screen-on locks (`[ReaderPlatformEffects.kt:ReaderPlatformEffects]`).

## Invariants
- `[EpubNavigatorHost.android.kt:EpubNavigatorFactory]`: Enforces single-column layout (`ColumnCount.ONE`) to prevent column overlap rendering bugs in Readium WebView when applying custom margins.
- `[EpubNavigatorHost.desktop.kt:DesktopLayout]`: Caches block measurements via `TextMeasurer` asynchronously on `Dispatchers.Default` to prevent UI thread layout freezing on window resize.
- `[EpubNavigatorHost.desktop.kt:RtlNavigation]`: Automatically mirrors keyboard arrow navigation when reading RTL Arabic text (Left Arrow advances page; Right Arrow returns to previous page).
- `[ReaderControls.kt:ReaderControls]`: Top and bottom controls render in floating overlays with zero layout push, ensuring reading container dimensions and text reflow are not triggered by toggling controls.
- `[ReaderScreen.kt:ReaderScreen]`: Hardware volume keys intercept page flips only when `volumeKeyNavigation` is enabled in preferences.

## Side Effects
- `[EpubNavigatorHost.android.kt]`: Mounts Android `FragmentContainerView` and hosts Readium WebView executing JavaScript pagination and text selection listeners.
- `[EpubNavigatorHost.desktop.kt]`: Renders Compose canvas text with Skia/HarfBuzz font shaping and draws visual highlight boxes across logical and BiDi text fragments.
- `[ManageSystemBars.android.kt]`: Modifies Android Window insets controller to enter and exit immersive sticky fullscreen mode on Android.
- `[ReaderPlatformEffects.android.kt]`: Alters Android `WindowManager.LayoutParams.screenBrightness` and `FLAG_KEEP_SCREEN_ON`.

## Verification
- Run: `./gradlew :feature:reader:assemble`
