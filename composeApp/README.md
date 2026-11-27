# Compose Application Entry

## Overview
Serves as the root application module, packaging coordinator, and presentation coordinator for KReader across Android and Desktop. Houses the top-level Compose entry point (`App.kt`), Android `MainActivity`, Desktop JVM launcher (`main.kt`), desktop packaging DSL, and system lifecycle bridges.

## Interface
- `App`: Root `@Composable` application orchestrating top-level screen switching between `LibraryScreen` and `ReaderScreen`, delegating business logic to `LibraryViewModel` and `ReaderViewModel` (`[App.kt:App]`).
- `MainActivity`: Android `FragmentActivity` hosting Compose content, configuring edge-to-edge system insets, handling incoming open-file intents (`.epub` and `.cbz`), and routing hardware volume key navigation (`[MainActivity.kt:MainActivity]`).
- `VaultSyncBridge`: Static callback bridge triggering repository vault refresh events upon application foreground resumption (`[VaultSyncBridge.kt:VaultSyncBridge]`).
- `main`: Desktop entry point accepting command-line arguments (`args: Array<String>`) to open EPUB and CBZ files directly from desktop file managers, creating an application window hosting `App()` (`[main.kt:main]`).

## Invariants
- `[MainActivity.kt:handleIncomingIntent]`: External book intents are transformed into `BookSource.FilePath` or `BookSource.ContentUri` and dispatched via `ExternalBookOpener.open()` into the library viewmodel without blocking the main UI thread.
- `[main.kt:main]`: File path arguments passed via CLI (`args[0]`) are validated, converted into `BookSource.FilePath`, and queued into `ExternalBookOpener.open()` prior to launching the UI window.
- `[MainActivity.kt:onKeyDown]`: Hardware volume button presses (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) are checked against `ReaderKeyEventDispatcher` and consumed only if active in reading mode.
- `[App.kt:App]`: If `selectedBook == null`, renders `LibraryScreen`; if `selectedBook != null`, renders `ReaderScreen`. Global settings mutations serialize through `BookRepository.updateReaderPreferences` so concurrent custom font updates are never overwritten.

## Side Effects
- `[MainActivity.kt:enableEdgeToEdge]`: Configures Android system window flags for edge-to-edge rendering.
- `[App.kt:VaultSyncBridge]`: Registers lifecycle resumption handler to refresh the vault file list upon returning to the app.

## Verification
- Assemble Android Debug: `./gradlew :composeApp:assembleDebug --configuration-cache`
- Run Desktop application: `./gradlew :composeApp:run`
- Package Desktop distribution: `./gradlew :composeApp:createDistributable`
