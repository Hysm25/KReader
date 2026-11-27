# CBZ Adapter Engine

## Overview
Implements native `.cbz` comic and manga archive parsing, `ComicInfo.xml` metadata extraction, natural page ordering, bounded SAF archive caching, and OOM-safe bitmap decoding with zero external comic-viewer dependencies.

## Interface
- `CbzNaturalSort`: Natural numeric path comparator (`page2.jpg` before `page10.jpg`) and image entry filter supporting `.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`, and `.bmp` while skipping directories, `__MACOSX/`, and AppleDouble `._*` entries (`[CbzNaturalSort.kt:CbzNaturalSort]`).
- `ComicInfo`: Metadata model representing `<Title>`, `<Series>`, `<Writer>`, `<Number>`, `<PageCount>`, and `<Manga>` reading direction hints (`[ComicInfo.kt:ComicInfo]`).
- `ComicInfoParser`: Pure Kotlin XML parser extracting `ComicInfo` fields and resolving comic display titles (`[ComicInfo.kt:ComicInfoParser]`).
- `CbzPageEntry`, `CbzArchiveMetadata`: Immutable page index directory and archive metadata models (`[CbzModels.kt:CbzArchiveMetadata]`).
- `CbzArchiveCache` (Android): Resolves `BookSource.FilePath` directly in place and copies `BookSource.ContentUri` SAF streams atomically into a bounded app cache (`cbz_archives/`, max 256 MB / 6 files) for O(1) `ZipFile` random page access (`[CbzArchiveCache.kt:CbzArchiveCache]`).
- `AndroidCbzArchive` (Android): `ZipFile`-backed archive reader decoding covers and pages safely on `Dispatchers.IO` with `inJustDecodeBounds`, hardware-safe texture limits (`3072px`/`4096px`), `RGB_565` optimization for opaque JPEGs, `BitmapRegionDecoder` vertical slicing for tall Webtoon strips, OOM retry fallback, and automatic Webtoon/Manga/Comic mode detection (`detectPreferredReadingMode`, checking `height / width >= 2.2` across initial pages) (`[AndroidCbzArchive.kt:AndroidCbzArchive]`).
- `CbzPageLoader` (Android): Bounded `LruCache` memory cache (`1/8` heap, clamped 24–96 MB) with concurrent load deduplication and adjacent page preloading (`current + 1`, `current + 2`, `current - 1`) (`[CbzPageLoader.kt:CbzPageLoader]`).
- `DesktopCbzArchive` (Desktop): JVM `ZipFile`-backed archive reader sharing `CbzNaturalSort` and `ComicInfoParser` (`[DesktopCbzArchive.kt:DesktopCbzArchive]`).

## Invariants
- `[CbzNaturalSort.kt:compare]`: Numeric runs are compared without `Int`/`Long` overflow by stripping leading zeros and comparing length then digits; path separators are normalized to `/`.
- `[CbzArchiveCache.kt:resolveContentUriToCache]`: Concurrent resolutions for the same URI share a per-key `Mutex`; writes stage to `.tmp` before atomic rename, and eviction never deletes the currently active archive.
- `[AndroidCbzArchive.kt:decodePage]`: Standard pages enforce both hardware texture limits (`<= 4096px`) and single-bitmap byte budgets (`<= 18 MB`); Webtoon strips taller than `3072px` are sliced vertically via `BitmapRegionDecoder` so tall strips remain readable without `Canvas` texture exceptions or `OutOfMemoryError`.
- `[CbzPageLoader.kt:preloadAround]`: Adjacent preloading cancels superseded jobs and bounds concurrent background decodes with `Semaphore(2)`.

## Side Effects
- `[CbzArchiveCache.kt]`: Copies SAF `content://` CBZ streams into `context.cacheDir/cbz_archives/` and evicts oldest cached archives when exceeding bounded capacity.
- `[AndroidCbzArchive.kt]`: Allocates native Android `Bitmap` instances on `Dispatchers.IO` and opens `java.util.zip.ZipFile` descriptors until `close()`.

## Verification
- Run: `./gradlew :engine:cbz-adapter:assemble`
