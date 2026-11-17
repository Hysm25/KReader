# Compose Application Entry

## Overview
Serves as the root application module and presentation coordinator for KReader. Houses the top-level Compose entry point (`App.kt`), Android `MainActivity`, Desktop JVM launcher (`main.kt`), and system lifecycle bridges.

## Interface
- `App`: Root `@Composable` application orchestrating top-level screen switching between `LibraryScreen` and `ReaderScreen`, delegating business logic to `LibraryViewModel` and `ReaderViewModel` (`[App.kt:App]`).
- `MainActivity`: Android `FragmentActivity` hosting Compose content, configuring edge-to-edge system insets, handling incoming open-file intents, and routing hardware volume key navigation (`[MainActivity.kt:MainActivity]`).
- `VaultSyncBridge`: Static callback bridge triggering repository vault refresh events upon application foreground resumption (`[VaultSyncBridge.kt:VaultSyncBridge]`).
- `main`: Desktop entry point creating an application window hosting `App()` (`[main.kt:main]`).

## Invariants
- `[MainActivity.kt:handleIncomingIntent]`: External book intents are transformed into `BookSource.FilePath` or `BookSource.ContentUri` and dispatched via `ExternalBookOpener.open()` into the library viewmodel without blocking the main UI thread.
- `[MainActivity.kt:onKeyDown]`: Hardware volume button presses (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) are checked against `ReaderKeyEventDispatcher` and consumed only if active in reading mode.
- `[App.kt:App]`: If `selectedBook == null`, renders `LibraryScreen`; if `selectedBook != null`, renders `ReaderScreen`.

## Side Effects
- `[MainActivity.kt:enableEdgeToEdge]`: Configures Android system window flags for edge-to-edge rendering.
- `[App.kt:VaultSyncBridge]`: Registers lifecycle resumption handler to refresh the vault file list upon returning to the app.

## Verification
- Run: `./gradlew :composeApp:assembleDebug --configuration-cache`
