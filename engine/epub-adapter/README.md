# EPUB Adapter Engine

## Overview
Implements EPUB publication loading and locator translation. On Android, wraps the Readium Kotlin Toolkit 3.0 (`readium-shared`, `readium-streamer`, `readium-navigator`) as the primary production engine to open publications, parse assets, and bridge Readium locators to domain locators. Desktop and Native (iOS) targets provide experimental progression simulation stubs for cross-platform UI prototyping without production EPUB rendering.

## Interface
- `EpubReaderEngine`: Platform-specific `expect/actual` engine implementing `ReaderEngine` (`[EpubReaderEngine.kt:EpubReaderEngine]`). Android integrates Readium 3.0; Desktop and Native are experimental simulation stubs.
- `ReadiumEpubOpener`: Android-specific publication loader using Readium's `AssetRetriever`, `DefaultHttpClient`, and `PublicationOpener` (`[ReadiumEpubOpener.kt:ReadiumEpubOpener]`).
- `toDomain`: Extension mapping Readium `Locator` instances to domain `Locator` entities (`[ReadiumLocatorMapper.kt:toDomain]`).
- `toReadium`: Extension mapping domain `Locator` entities to Readium `Locator` instances (`[ReadiumLocatorMapper.kt:toReadium]`).
- `toTocItem`: Extension mapping Readium navigation links to domain `TocItem` hierarchy (`[ReadiumLocatorMapper.kt:toTocItem]`).

## Invariants
- `[ReadiumLocatorMapper.kt:toReadium]`: Missing or unparseable URLs fall back to `Url("#")` and missing media types fall back to `MediaType.BINARY` to prevent mapping crashes.
- `[AndroidEpubReaderEngine.kt:open]`: `initialize(context)` must be invoked prior to calling `open()` to instantiate the underlying `ReadiumEpubOpener`.
- `[DesktopEpubReaderEngine.kt:next/previous]`: Experimental desktop stub clamps progression values within `[0.0, 1.0]`.

## Side Effects
- `[ReadiumEpubOpener.kt:open]`: Reads EPUB archives from local storage or Android `ContentResolver`, unzips and parses container XML, package documents, and encryption manifests into Readium `Publication` memory structures.
- `[AndroidEpubReaderEngine.kt:search]`: Executes Readium full-text search iterations across publication resources on Android.

## Verification
- Run: `./gradlew :engine:epub-adapter:assemble`
