# Feature Reader

## Overview
Provides the multiplatform EPUB reading viewport, Readium navigator hosting on Android, native Compose Block AST reader hosting on Desktop, floating controls overlay, typography and theme customization sheets, table of contents navigation, full-text search, and annotation management.

## Interface
- `ReaderViewModel`: Presentation state holder orchestrating reading sessions, bookmarks, highlights, notes, search queries, TOC, and sparse per-property typography/theme overrides (`[ReaderViewModel.kt:ReaderViewModel]`).
- `rememberReaderViewModel`: Composable factory hook creating and retaining the `ReaderViewModel` instance (`[ReaderViewModel.kt:rememberReaderViewModel]`).
- `ReaderScreen`: Top-level Composable scaffolding the reading container (with tablet responsive centering up to 840dp), floating top bar displaying book and current chapter title, bottom scrubber bar, settings sheets, search drawer, and note dialogs (`[ReaderScreen.kt:ReaderScreen]`).
- `ReaderUiState`: Immutable state model holding reading session data, controls visibility, drawer states, search results, themes, and current locators (`[ReaderUiState.kt:ReaderUiState]`).
- `EpubNavigatorHost`: Multiplatform Compose bridge hosting the platform EPUB navigator (`[EpubNavigatorHost.kt:EpubNavigatorHost]`).
  - **Android**: Embeds Readium's `EpubNavigatorFragment` inside an `AndroidView`, submits official `EpubPreferences`, and resolves the book-wide page count from `Publication.positions()`.
  - **Desktop (Linux/JVM)**: Embeds an in-house pure Compose Multiplatform reading viewport supporting continuous vertical scrolling, virtual single/dual-page pagination with `TextMeasurer`, `.kreader/` vault theme styling, custom font loading, native RTL/LTR Arabic BiDi rendering, highlight creation (`TextQuoteSelector`), TOC jumps, and in-book search.
- `EpubNavigatorController`: Imperative controller exposing programmatic navigation hooks (`goTo`, `goNext`, `goPrevious`, `seekToProgression`, `search`, `highlightSearchResult`) (`[EpubNavigatorController.kt:EpubNavigatorController]`).
- `ReaderSettingsSheet`: Bottom sheet modal with curated built-in theme presets (System, Light, Sepia, Dark, AMOLED), typography adjustments, scroll mode, and orientation locks (`[ReaderSheets.kt:ReaderSettingsSheet]`).
- `ReaderControls`: Floating top and bottom reading bars with chapter title display, smooth drag progress scrubber, and chapter jump controls (`[ReaderControls.kt:ReaderControls]`).
- `ManageSystemBars`: Platform effect managing edge-to-edge immersive sticky fullscreen (`[ManageSystemBars.kt:ManageSystemBars]`).
- `ReaderPlatformEffects`: Platform effect applying display brightness and screen-on locks (`[ReaderPlatformEffects.kt:ReaderPlatformEffects]`).

## Invariants
- `[EpubPreferencesBuilder.kt:buildReadiumPreferences]`: Enables Readium CSS advanced settings (`publisherStyles = false`) so the live line-height, alignment and margin controls are effective; author stylesheets are still injected, and an explicit `typeScale = 1.25` keeps `h1`-`h6` proportional and hierarchical while centered `<hr>` and RTL flow stay untouched.
- `[EpubPreferencesBuilder.kt:readiumFontFamily]`: Returns `null` unless the user explicitly enables the font override; generic families are normalized to canonical CSS names, and imported families resolve to font faces declared by the navigator configuration.
- `[EpubNavigatorHost.android.kt]`: Uses `WebSettings.textZoom` (`useReadiumCssFontSize = false`) so live font scaling also affects hard-coded `px`/`pt` publisher stylesheets.
- `[EpubNavigatorHost.android.kt]`: Derives the page counter from `publication.positions()` and the authoritative `Locator.locations.position` emitted by Readium's `PositionsService`, never from per-resource `PaginationListener` totals.
- `[EpubNavigatorHost.android.kt]`: Applies highlights and search focus exclusively through `DecorableNavigator.applyDecorations`; the navigator retains decorations across chapter loads.
- `[EpubNavigatorHost.android.kt]`: Reads selections through `SelectableNavigator.currentSelection()` and clears them through `clearSelection()`; no DOM or JavaScript access is performed.
- `[EpubNavigatorHost.desktop.kt:DesktopLayout]`: Caches block measurements via `TextMeasurer` asynchronously on `Dispatchers.Default` to prevent UI thread layout freezing on window resize.
- `[EpubNavigatorHost.desktop.kt:RtlNavigation]`: Automatically mirrors keyboard arrow navigation when reading RTL Arabic text (Left Arrow advances page; Right Arrow returns to previous page).
- `[ReaderControls.kt:ReaderControls]`: Top and bottom controls render in floating overlays with zero layout push, ensuring reading container dimensions and text reflow are not triggered by toggling controls.
- `[ReaderScreen.kt:ReaderScreen]`: Hardware volume keys intercept page flips only when `volumeKeyNavigation` is enabled in preferences.

## Side Effects
- `[EpubNavigatorHost.android.kt]`: Mounts an Android `FragmentContainerView`, hosts the Readium `EpubNavigatorFragment`, submits `EpubPreferences`, and registers official selection, pagination and decoration listeners.
- `[EpubNavigatorHost.desktop.kt]`: Renders Compose canvas text with Skia/HarfBuzz font shaping and draws visual highlight boxes across logical and BiDi text fragments.
- `[ManageSystemBars.android.kt]`: Modifies Android Window insets controller to enter and exit immersive sticky fullscreen mode on Android.
- `[ReaderPlatformEffects.android.kt]`: Alters Android `WindowManager.LayoutParams.screenBrightness` and `FLAG_KEEP_SCREEN_ON`.

## Verification
- Run: `./gradlew :feature:reader:assemble`
