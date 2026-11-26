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
- `EpubNavigatorController`: Imperative controller exposing programmatic navigation hooks (`goTo`, `goToSearchResult`, `goNext`, `goPrevious`, `seekToProgression`, `search`, `highlightSearchResult`, `clearSearchHighlight`, `clearSelection`) (`[EpubNavigatorController.kt:EpubNavigatorController]`).
- `EpubStyleNormalizer`: CSS & DOM font-scale normalizer wrapping fixed `px`/`pt`/`in`/`cm`/`mm`/`pc` font-size rules with `calc(... * var(--kreader-font-scale, 1))` (`[EpubStyleNormalizer.kt:EpubStyleNormalizer]`).
- `EpubBlockParser`: Desktop Jsoup-based XHTML tokenizer converting chapters into `ReaderContentBlock` AST nodes (`[DesktopReaderBlock.kt:EpubBlockParser]`).
- `ReaderSettingsSheet`: Bottom sheet modal with curated built-in theme presets (System, System Light, Light, Sepia, Dark, AMOLED), typography adjustments, scroll mode, volume key toggle, and orientation locks (`[ReaderSheets.kt:ReaderSettingsSheet]`).
- `ReaderControls`: Floating top and bottom reading bars (`ReaderTopBar`, `ReaderBottomBar`) with chapter title display, page/progress indicator, smooth drag progress scrubber, and chapter jump controls (`[ReaderControls.kt:ReaderTopBar]`).
- `ManageSystemBars`: Platform effect managing edge-to-edge system bar icon contrast (`[ManageSystemBars.kt:ManageSystemBars]`).
- `ReaderPlatformEffects`: Platform effects applying keep-screen-on (`KeepScreenOn`) and orientation locks (`ManageOrientation`) (`[ReaderPlatformEffects.kt:KeepScreenOn]`).

## Invariants
- `[EpubPreferencesBuilder.kt:buildReadiumPreferences]`: Preserves native publisher styling (`publisherStyles = true`, `typeScale = null`) so EPUB original CSS (heading proportions `h1`–`h6`, centered `<hr>` dividers, blockquotes, and Arabic RTL layout) renders faithfully without artificial scale distortion.
- `[EpubPreferencesBuilder.kt:readiumFontFamily]`: Returns `null` unless the user explicitly enables the font override; generic families are normalized to canonical CSS names, and imported custom fonts resolve to `ReadiumFontResources.cssFamilyName` so ReadiumCSS and `#kreader-font-override` share an identical sanitized identifier.
- `[EpubPreferencesBuilder.kt:buildFontOverrideCss]`: Emits `@font-face` rules spanning `font-weight: 100 900` across both `normal` and `italic` styles with format hints (`truetype`, `opentype`, `woff`, `woff2`), Readium publication URL primary source, and inline `data:` fallback (for fonts <= 4 MB) so Arabic paragraphs, bold headings, and inline spans render with imported custom fonts without falling back to system fonts.
- `[EpubNavigatorHost.android.kt]`: Combines `WebSettings.textZoom` (`useReadiumCssFontSize = false`) and `EpubStyleNormalizer` (marking `data-kreader-scaled` for O(1) live slider scaling) so font scaling affects all publications (including hard-coded `px`/`pt` stylesheets) while preserving proportional heading-to-body ratios; evaluates overrides once per WebView.
- `[EpubNavigatorHost.android.kt]`: When `overrideFont` is enabled, applies the chosen font family uniformly across all text elements (`body`, `h1`–`h6`, `p`, `div`, `span`, `li`, `blockquote`, `a`, inline formatting, and table cells); when disabled, cleanly yields 100% back to embedded publisher fonts. Chapter transitions read live typography via `rememberUpdatedState`, and override keys reset whenever a new `navigator` instance attaches.
- `[EpubNavigatorHost.android.kt]`: Derives the page counter from `publication.positions()` and the authoritative `Locator.locations.position` emitted by Readium's `PositionsService`, never from per-resource `PaginationListener` totals.
- `[EpubNavigatorHost.android.kt]`: Applies highlights and search focus exclusively through `DecorableNavigator.applyDecorations` (with contrast-tuned alpha across Light, Sepia, Dark, AMOLED, and Custom palettes) and re-aligns `goToSearchResult` pagination after post-navigation typography reflow settles so search jumps land on the exact matching page.
- `[EpubNavigatorHost.android.kt]`: Reads selections through `SelectableNavigator.currentSelection()` via `ActionMode.Callback2` (supporting handle drag updates across LTR and RTL text), normalizes RTL/LTR `SelectionMenu` bounds, snaps initial coordinates without fly-in flicker, and clears them through `clearSelection()`.
- `[ReaderViewModel.kt]`: Resets cancel any pending debounced `typographySaveJob` and disposal flushes pending typography overrides via `NonCancellable` so rapid back navigation preserves slider edits; `findTocTitleForHref` resolves nested TOC titles recursively.
- `[EpubNavigatorHost.desktop.kt:DesktopLayout]`: Caches block measurements via `TextMeasurer` asynchronously on `Dispatchers.Default` to prevent UI thread layout freezing on window resize.
- `[EpubNavigatorHost.desktop.kt:RtlNavigation]`: Automatically mirrors keyboard arrow navigation when reading RTL Arabic text (Left Arrow advances page; Right Arrow returns to previous page).
- `[ReaderControls.kt:ReaderBottomBar]`: Top and bottom controls render in floating overlays with zero layout push; the bottom scrubber displays a concise LTR-isolated page/percentage pill (`<page> / <total>` or `<pct>%`) that never truncates or flips in RTL UI.
- `[ReaderScreen.kt:ReaderScreen]`: Hardware volume keys intercept page flips only when `volumeKeyNavigation` is enabled in preferences.

## Side Effects
- `[EpubNavigatorHost.android.kt]`: Mounts an Android `FragmentContainerView`, hosts the Readium `EpubNavigatorFragment`, submits `EpubPreferences`, and registers official selection, pagination and decoration listeners.
- `[EpubNavigatorHost.desktop.kt]`: Renders Compose canvas text with Skia/HarfBuzz font shaping and draws visual highlight boxes across logical and BiDi text fragments.
- `[ManageSystemBars.android.kt]`: Modifies Android Window insets controller to enter and exit immersive sticky fullscreen mode on Android.
- `[ReaderPlatformEffects.android.kt]`: Alters Android `WindowManager.LayoutParams.screenBrightness` and `FLAG_KEEP_SCREEN_ON`.

## Verification
- Run: `./gradlew :feature:reader:assemble`
