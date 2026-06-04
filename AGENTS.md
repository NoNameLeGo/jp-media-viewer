# Repository Guidelines

## Project Overview
JP Media Viewer is a single-module Android app for browsing local images and videos picked through the Storage Access Framework (SAF). Users add one or more folders, the app scans recursively, caches results locally, supports favorites, and lets users browse media with swipe and tap gestures.

## Architecture & Data Flow
- **Entry point:** `android/app/src/main/java/com/jp/app/MainActivity.kt`
- **Flow:** folder URIs from SAF → `MediaScanner` recursion → cached JSON in `SharedPreferences` → Compose picker/viewer screens.
- **State ownership:** the activity/composable tree owns orchestration directly; there is no ViewModel, repository, or DI container.
- **Caching:** scan results, folder set, `.nomedia` preference, and favorites are persisted in `SharedPreferences`.
- **Async boundaries:** scanning and cache load/write use `Dispatchers.IO`; UI state is managed with Compose state APIs (`remember`, `mutableStateOf`, `LaunchedEffect`, `DisposableEffect`).
- **Media playback:** videos use Media3/ExoPlayer with `DisposableEffect` cleanup.

## Key Directories
- `android/app/src/main/java/com/jp/app/`
  - `MainActivity.kt` — app state hub and screen switching.
  - `data/MediaScanner.kt` — SAF traversal, MIME filtering, progress callbacks.
  - `data/Models.kt` — shared domain models.
  - `ui/FolderPickerScreen.kt` — folder selection, scanning status, about dialog.
  - `ui/MediaViewerScreen.kt` — full-screen viewer, gestures, image/video rendering.
- `android/app/src/main/` — Android manifest and app resources/theme wiring.
- `.github/workflows/build.yml` — CI build and release workflow.
- `README.md` — product summary, usage, and build commands.
- `feature.md` — follow-up issues and roadmap notes.
- `docs/icon-prompt.md` — launcher icon design brief.

## Development Commands
Run from `android/`:
- `./gradlew assembleDebug`
- Windows: `gradlew.bat assembleDebug`
- CI runs `./gradlew assembleDebug --no-daemon`
- Output APKs: debug `app/build/outputs/apk/debug/jp-media-viewer-beta0.0.8-debug.apk`; release `app/build/outputs/apk/release/jp-media-viewer-beta0.0.8-release.apk`

## Code Conventions & Common Patterns
- **UI stack:** Jetpack Compose + Material3. Screens are composables in `ui/`.
- **State:** prefer Compose state (`remember`, `mutableStateOf`, `LaunchedEffect`) over external state containers.
- **Async work:** keep filesystem/serialization work on `Dispatchers.IO`; release resources with `DisposableEffect`.
- **Persistence:** use `SharedPreferences` for small app state; cached media lists are serialized to JSON.
- **Media model:** `MediaItem` derives `isImage` / `isVideo` from MIME type.
- **Error handling:** `runCatching` is used for fallible intent/persistable-permission operations; scanning catches `SecurityException` separately.
- **Naming:** keep package names under `com.jp.app`; follow existing `Media*`, `*Screen`, and `*Scanner` naming.

## Important Files
- `android/app/src/main/AndroidManifest.xml` — launcher activity, backup policy, theme wiring.
- `android/app/build.gradle.kts` — app SDK levels, Compose setup, dependencies, versioning.
- `android/build.gradle.kts` and `android/settings.gradle.kts` — root plugin versions and single-module wiring.
- `android/gradle.properties` — AndroidX/non-transitive R and JVM settings.
- `android/gradle/wrapper/gradle-wrapper.properties` — pinned Gradle version.
- `README.md` — product behavior and build instructions.
- `.github/workflows/build.yml` — CI/release behavior.

## Runtime/Tooling Preferences
- **Required runtime:** Android/JDK 17 for builds.
- **Build system:** Gradle Kotlin DSL via the wrapper in `android/`.
- **Package/dependency resolution:** Google Maven + Maven Central, with Gradle Plugin Portal only for plugin resolution.
- **Tooling constraints:** AndroidX enabled, non-transitive R classes, UTF-8 file encoding, official Kotlin code style.

## Testing & QA
- There is no checked-in unit/instrumentation test suite or test framework configuration.
- Current verification is build-centric: run `./gradlew assembleDebug` locally and rely on `.github/workflows/build.yml` for CI parity.
- When changing runtime behavior, verify the debug build still succeeds and update only the affected code paths.