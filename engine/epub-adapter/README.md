# EPUB Adapter Engine

## Overview
Implements EPUB publication loading, parsing, and locator translation across platforms.
- **Android**: Wraps the Readium Kotlin Toolkit 3.0 (`readium-shared`, `readium-streamer`, `readium-navigator`) as the mobile engine to open publications, parse assets, and bridge Readium locators to domain locators.
- **Desktop (Linux/JVM)**: Implements an in-house pure Compose Block AST EPUB engine (`DesktopEpubReaderEngine`). Parses EPUB container XML and OPF package manifests via standard ZIP traversal, extracts chapters using Jsoup into typed `ReaderContentBlock` AST representations, parses NCX/NavDoc tables of contents, provides streaming in-book search, and computes Readium-compatible `Locator` records with deterministic Unicode NFC normalization and whitespace collapsing.
- **Native (iOS)**: Progression simulation stub for UI prototyping.

## Interface
- `EpubReaderEngine`: Platform-specific `expect/actual` engine implementing `ReaderEngine` (`[EpubReaderEngine.kt:EpubReaderEngine]`).
- `DesktopEpubReaderEngine`: Desktop production implementation of `ReaderEngine` (`[DesktopEpubReaderEngine.kt:DesktopEpubReaderEngine]`).
- `DesktopEpubDocument`: Desktop EPUB container, metadata, spine, TOC, and resource extractor.
- `EpubBlockParser`: Jsoup-based XHTML tokenizer converting book chapters into `ReaderContentBlock` AST nodes.
- `ReadiumEpubOpener`: Android-specific publication loader using Readium's `AssetRetriever`, `DefaultHttpClient`, and `PublicationOpener` (`[ReadiumEpubOpener.kt:ReadiumEpubOpener]`).
- `ReadiumFontResources`: Derives stable publication hrefs and absolute navigator URLs for user-imported font files (`[ReadiumFontResources.kt:ReadiumFontResources]`).
- `toDomain`: Extension mapping Readium `Locator` instances to domain `Locator` entities (`[ReadiumLocatorMapper.kt:toDomain]`).
- `toReadium`: Extension mapping domain `Locator` entities to Readium `Locator` instances (`[ReadiumLocatorMapper.kt:toReadium]`).
- `toTocItem`: Extension mapping Readium navigation links to domain `TocItem` hierarchy (`[ReadiumLocatorMapper.kt:toTocItem]`).

## Invariants
- `[ReadiumLocatorMapper.kt:toReadium]`: Missing or unparseable URLs fall back to `Url("#")` and missing media types fall back to `MediaType.BINARY` to prevent mapping crashes.
- `[AndroidEpubReaderEngine.kt:open]`: `initialize(context)` must be invoked prior to calling `open()` to instantiate the underlying `ReadiumEpubOpener`.
- `[DesktopEpubReaderEngine.kt:open]`: Reads ZIP entries thread-safely via `java.util.zip.ZipFile`, builds manifest lookup tables, and maps navigation items to canonical resource hrefs.
- `[DesktopEpubReaderEngine.kt:search]`: Emits streaming search results across spine items on `Dispatchers.Default` with coroutine cooperative cancellation and Arabic diacritic normalization.
- `[Text Normalization]`: Text extracted for `TextQuoteSelector` (`exact`, `prefix`, `suffix`) undergoes Unicode NFC normalization and contiguous whitespace collapsing (`[\r\n\t ]+` -> `' '`) to ensure cross-platform parity between Readium DOM and Desktop AST.

## Side Effects
- `[ReadiumEpubOpener.kt:open]`: Reads EPUB archives from local storage or Android `ContentResolver`, unzips and parses container XML, package documents, and encryption manifests into Readium `Publication` memory structures. User-imported fonts are wrapped as publication resources served at `https://readium/publication/kreader-fonts/*`.
- `[DesktopEpubReaderEngine.kt:open]`: Reads container XML (`META-INF/container.xml`), parses OPF package manifests, and caches extracted chapter metadata in memory.
- `[AndroidEpubReaderEngine.kt:search]`: Executes Readium full-text search iterations across publication resources on Android.

## Verification
- Run: `./gradlew :engine:epub-adapter:assemble`
