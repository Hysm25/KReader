# Engine Subsystem

## Overview
Publication rendering and reading engine adapters for KReader, encapsulating format-specific parsers, stream decoders, and locator mapping tools.

## Subdirectories & Boundaries
- `epub-adapter`: [./epub-adapter/README.md](./epub-adapter/README.md) - Readium 3.0 EPUB publication opener, domain locator mapper, and platform reader engine adapters.
- `cbz-adapter`: [./cbz-adapter/README.md](./cbz-adapter/README.md) - Native `ZipFile` CBZ archive reader, natural page sorter, `ComicInfo.xml` parser, bounded SAF cache, and OOM-safe bitmap decoder & preloader.
