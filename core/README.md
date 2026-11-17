# Core Subsystem

## Overview
Foundational multiplatform subsystem providing domain models, local-first database persistence, and reader engine contracts for KReader.

## Subdirectories & Boundaries
- `model`: [./model/README.md](./model/README.md) - Domain entities, locators, reading preferences, color themes, and RTL detection utilities.
- `database`: [./database/README.md](./database/README.md) - Obsidian-style `.kreader/` JSON vault persistence, repository layer, and Markdown export tools.
- `engine-api`: [./engine-api/README.md](./engine-api/README.md) - Reader engine interface abstractions, engine lifecycle state machines, and hardware key dispatchers.
