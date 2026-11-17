# Core Engine API

## Overview
Defines publication engine contracts, reading states, lifecycle events, table of contents models, and hardware key event dispatchers. Serves as the abstraction boundary separating reading UI components from concrete reader runtime implementations (such as Readium Kotlin Toolkit).

## Interface
- `ReaderEngine`: Abstraction contract defining publication lifecycle methods (`open`, `close`, `goTo`, `next`, `previous`, `applyTheme`, `applyTypography`, `search`) and reactive state/event streams (`[ReaderEngine.kt:ReaderEngine]`).
- `ReaderEngineState`: Sealed interface representing engine states (`Idle`, `Loading`, `Ready`, `Error`) (`[ReaderEngineState.kt:ReaderEngineState]`).
- `ReaderEngineEvent`: Sealed interface representing discrete reader events (`PageChanged`, `TextSelected`, `HighlightClicked`, `Tap`, `ErrorOccurred`) (`[ReaderEngineState.kt:ReaderEngineEvent]`).
- `TocItem`: Nested hierarchical table of contents data class linking titles to target locators (`[TableOfContents.kt:TocItem]`).
- `ReaderKeyEventDispatcher`: Global dispatch object forwarding hardware volume key events from platform activities to active reader sessions (`[ReaderKeyEventDispatcher.kt:ReaderKeyEventDispatcher]`).

## Invariants
- `[ReaderEngineState.kt:ReaderEngineState]`: Engine state transitions must flow from `Idle` -> `Loading` -> `Ready` (or `Error`). Navigation actions (`goTo`, `next`, `previous`) are rejected with `IllegalStateException` when not in `Ready` state.
- `[ReaderKeyEventDispatcher.kt:ReaderKeyEventDispatcher]`: Key handlers return `Boolean` indicating whether the hardware event was consumed; unhandled events fall through to system dispatchers.

## Side Effects
- `[ReaderKeyEventDispatcher.kt:ReaderKeyEventDispatcher]`: Mutates global function callbacks for volume key interception. No disk or network I/O.

## Verification
- Run: `./gradlew :core:engine-api:assemble`
