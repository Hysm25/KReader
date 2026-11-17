# Feature Reader

## Overview
Provides the EPUB reading viewport, Readium navigator hosting, floating controls overlay, typography and theme customization sheets, table of contents navigation, full-text search, and annotation management.

## Interface
- `ReaderViewModel`: Presentation state holder orchestrating reading sessions, bookmarks, highlights, notes, search queries, TOC, and typography overrides (`[ReaderViewModel.kt:ReaderViewModel]`).
- `rememberReaderViewModel`: Composable factory hook creating and retaining the `ReaderViewModel` instance (`[ReaderViewModel.kt:rememberReaderViewModel]`).
- `ReaderScreen`: Top-level Composable scaffolding the reading container, floating top and bottom app bars, settings sheets, search drawer, and note dialogs (`[ReaderScreen.kt:ReaderScreen]`).
- `ReaderUiState`: Immutable state model holding reading session data, controls visibility, drawer states, search results, themes, and current locators (`[ReaderUiState.kt:ReaderUiState]`).
- `EpubNavigatorHost`: Multiplatform Compose bridge hosting the platform EPUB navigator (`[EpubNavigatorHost.kt:EpubNavigatorHost]`). On Android, embeds Readium's `EpubNavigatorFragment` inside an `AndroidView`. On Desktop, provides an experimental text placeholder stub.
- `EpubNavigatorController`: Imperative controller exposing programmatic navigation hooks (`goTo`, `goNext`, `goPrevious`, `seekToProgression`, `search`, `highlightSearchResult`) (`[EpubNavigatorController.kt:EpubNavigatorController]`).
- `ReaderSettingsSheet`: Bottom sheet modal for adjusting font family, font size, line height, themes, margins, scroll mode, and orientation locks (`[ReaderSheets.kt:ReaderSettingsSheet]`).
- `ReaderControls`: Floating top and bottom reading bars with progress scrubber and chapter jump controls (`[ReaderControls.kt:ReaderControls]`).
- `ManageSystemBars`: Platform effect managing edge-to-edge immersive sticky fullscreen (`[ManageSystemBars.kt:ManageSystemBars]`).
- `ReaderPlatformEffects`: Platform effect applying display brightness and screen-on locks (`[ReaderPlatformEffects.kt:ReaderPlatformEffects]`).

## Invariants
- `[EpubNavigatorHost.android.kt:EpubNavigatorFactory]`: Enforces single-column layout (`ColumnCount.ONE`) to prevent column overlap rendering bugs in Readium WebView when applying custom margins.
- `[ReaderControls.kt:ReaderControls]`: Top and bottom controls render in floating overlays with zero layout push, ensuring reading container dimensions and text reflow are not triggered by toggling controls.
- `[ReaderScreen.kt:ReaderScreen]`: Hardware volume keys intercept page flips only when `volumeKeyNavigation` is enabled in preferences.

## Side Effects
- `[EpubNavigatorHost.android.kt]`: Mounts Android `FragmentContainerView` and hosts Readium WebView executing JavaScript pagination and text selection listeners.
- `[ManageSystemBars.android.kt]`: Modifies Android Window insets controller to enter and exit immersive sticky fullscreen mode on Android.
- `[ReaderPlatformEffects.android.kt]`: Alters Android `WindowManager.LayoutParams.screenBrightness` and `FLAG_KEEP_SCREEN_ON`.

## Verification
- Run: `./gradlew :feature:reader:assemble`
