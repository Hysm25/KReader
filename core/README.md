# Core Subsystem

## Overview
Foundational multiplatform subsystem providing domain models, local-first database persistence, and reader engine contracts for KReader across Android and Desktop.

## Subdirectories & Boundaries
- `model`: [./model/README.md](./model/README.md) - Domain entities (`Book`, `Locator`, `Bookmark`, `Highlight`, `ReaderPreferences`, `ReaderTheme`), W3C `TextQuoteSelector` contracts, and RTL detection utilities.
- `database`: [./database/README.md](./database/README.md) - Obsidian-style `.kreader/` JSON vault persistence (`workspace.json`, `annotations.json`, `appearance.json`, `themes.json`, `covers/`, `fonts/`), repository layer, and Markdown export tools.
- `engine-api`: [./engine-api/README.md](./engine-api/README.md) - Reader engine interface abstractions (`ReaderEngine`), lifecycle state machines, hardware key dispatchers, and table of contents contracts.
