# Repository Guidelines

## Project Overview
JP Media Viewer is a single-module Android app for browsing local images and videos picked through the Storage Access Framework (SAF). Users add one or more folders, the app scans recursively, caches results locally, supports favorites, and lets users browse media with swipe and tap gestures.

- **License:** AGPL-3.0-only
- **Current version:** beta0.5.0 (versionCode 26)
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3

## Architecture & Data Flow
- **Entry point:** `android/app/src/main/java/com/jp/app/MainActivity.kt`
- **Flow:** folder URIs from SAF → `MediaScanner` recursion on `Dispatchers.IO` → JSON-serialized list in `SharedPreferences` → shuffled for random browse → `MediaViewerScreen`
- **State ownership:** the activity/composable tree owns orchestration directly; there is no ViewModel, repository, or DI container. All app state lives as `remember`/`mutableStateOf` variables in the `MainApp` composable inside `MainActivity.kt`.
- **Screen routing:** a simple `if/else` on an `isViewing` boolean. `FolderPickerScreen` when not viewing, `MediaViewerScreen` when viewing. An `isFavoriteBrowsing` boolean controls the filter for favorites mode.
- **Async boundaries:** scanning and cache load/write use `Dispatchers.IO`; UI state is managed with Compose state APIs (`remember`, `mutableStateOf`, `LaunchedEffect`, `DisposableEffect`).
- **Media playback:** videos use Media3/ExoPlayer with `DisposableEffect` cleanup in a `AndroidView` wrapping `PlayerView`.

## Key Directories
- `android/app/src/main/java/com/jp/app/`
  - `MainActivity.kt` — app state hub, screen switching, cache persistence, theme setup.
  - `data/MediaScanner.kt` — SAF traversal, MIME filtering, progress callbacks.
  - `data/Models.kt` — `MediaItem` and `AppSettings` data classes.
  - `ui/FolderPickerScreen.kt` — folder selection, scanning status, cache management, about dialog.
  - `ui/MediaViewerScreen.kt` — full-screen viewer with gestures, image/video rendering, zoom.
- `android/app/src/main/` — Android manifest, app resources, theme, backup/extraction rules.
- `android/app/src/main/res/xml/` — `backup_rules.xml`, `data_extraction_rules.xml`.
- `.github/workflows/` — `build.yml` CI definition.
- `docs/` — design briefs and notes.

## Development Commands
- **Do not run local Gradle builds during agent development.** Agents must not invoke `./gradlew`, `gradlew.bat`, or local `assemble*` tasks for verification.
- **Build verification:** use GitHub Actions (`.github/workflows/build.yml`) to build debug APKs and release APKs.
- **Human/local fallback only:** local commands remain `./gradlew assembleDebug` / `gradlew.bat assembleDebug` and `./gradlew assembleRelease`, but agents should leave them to the user unless explicitly instructed otherwise.
- **PR lifecycle for agents:** after a PR build passes in GitHub Actions, agents must merge the PR and delete the branch before starting release builds or yielding, unless the user explicitly asks to leave the PR open.
- **Output APKs:**
  - Debug: `app/build/outputs/apk/debug/jp-media-viewer-{versionName}-debug.apk`
  - Release: `app/build/outputs/apk/release/jp-media-viewer-{versionName}-release.apk`

## Code Conventions & Common Patterns
- **UI stack:** Jetpack Compose + Material3. Screens are composable functions in `ui/` package prefixed by the screen name (e.g. `FolderPickerScreen`, `MediaViewerScreen`).
- **State management:** all app-level state uses `remember { mutableStateOf(...) }` inside `MainApp`. Local UI state (dialogs, toggles) uses `remember { mutableStateOf(...) }` inside the screen composable. Helper lambdas in the caller (`saveFolders`, `rescanMedia`, `toggleFavorite`) update both Compose state and `SharedPreferences` via `prefs.edit().apply()`.
- **Screen parameters:** screen composables receive data as parameters and emit events via lambda callbacks (no navigation component, no view models). Example signature:
  ```kotlin
  fun FolderPickerScreen(
      folders: List<String>,
      onFoldersChanged: (List<String>) -> Unit,
      isScanning: Boolean,
      ...
  )
  ```
- **Async work:** filesystem/serialization runs on `Dispatchers.IO` via `withContext(Dispatchers.IO)`. Scanning uses `suspend` functions with progress callbacks: `suspend fun scan(..., onProgress: suspend (ScanProgress) -> Unit)`.
- **Persistence:** `SharedPreferences` for small app state — folder URIs (as `String` set), `.nomedia` preference, favorites Uris. Cached media lists are serialized to JSON using `org.json.JSONObject`/`org.json.JSONArray` (no kotlinx.serialization or Gson). See `loadCachedMediaScan`/`saveCachedMediaScan`/`clearCachedMediaScan` in `MainActivity.kt`.
- **Media model (`data/Models.kt`):**
  ```kotlin
  data class MediaItem(
      val uri: Uri, val name: String, val mimeType: String,
      val size: Long, val folderUri: Uri, val modifiedAt: Long
  ) {
      val isVideo: Boolean get() = mimeType.startsWith("video/")
      val isImage: Boolean get() = mimeType.startsWith("image/")
  }
  data class AppSettings(
      val folderUris: List<String> = emptyList(),
      val respectNomedia: Boolean = true
  )
  ```
- **Error handling:** `runCatching` for fallible intent operations (opening URLs, SAF permission grants). Scanning catches `SecurityException` separately for permission-denied directories. CancellationException is rethrown. Media load errors set a `mediaLoadError` flag that triggers an AlertDialog.
- **Naming:** package `com.jp.app`; classes `Media*`, `*Screen`, `*Scanner`. Files match the class name.
- **Image rendering:** Coil via `SubcomposeAsyncImage` with `cachePolicy = CachePolicy.DISABLE` (reloads on nav) and `ImageRequest.Builder` for `crossfade(true)`.
- **Video rendering:** `AndroidView` wrapping `PlayerView` with `ExoPlayer.Builder(context).build()`; `DisposableEffect` releases the player on disposal. Uses `MediaItem.fromUri()` (import aliased as `ExoMediaItem` to avoid name clash with domain model).
- **Gesture patterns:** custom `pointerInput` blocks using `detectTapGestures`, `detectVerticalDragGestures`, and `awaitEachGesture` for swipe logic. Pinch-zoom uses `detectTransformGestures` with `graphicsLayer` scale and offset transforms, plus settled-zoom snapping (1f, 1.5f, 2f, 4f).
- **Backup:** explicitly disabled (`android:allowBackup="false"`). `backup_rules.xml` and `data_extraction_rules.xml` exclude `settings.xml` from full backup, cloud backup, and device transfer.
- **Theming:** dynamic Material You colors on Android 12+ (`dynamicDarkColorScheme`/`dynamicLightColorScheme`); manual dark/light `ColorScheme` fallback on older versions.

## Important Files
- `android/app/src/main/java/com/jp/app/MainActivity.kt` — app entry, all state, cache IO, screen routing.
- `android/app/src/main/java/com/jp/app/data/MediaScanner.kt` — recursive SAF directory scanner on IO dispatcher.
- `android/app/src/main/java/com/jp/app/data/Models.kt` — `MediaItem` and `AppSettings` domain models.
- `android/app/src/main/java/com/jp/app/ui/FolderPickerScreen.kt` — folder management, scan status, about.
- `android/app/src/main/java/com/jp/app/ui/MediaViewerScreen.kt` — full-screen viewer with all gestures.
- `android/app/src/main/AndroidManifest.xml` — launcher activity, backup policy, theme wiring.
- `android/app/build.gradle.kts` — SDK levels (compileSdk=34, minSdk=26, targetSdk=34), Compose setup, dependencies, release signing via env vars.
- `android/build.gradle.kts` — root plugin versions (AGP 8.2.2, Kotlin 1.9.22).
- `android/settings.gradle.kts` — single-module wiring (`:app`), Google/Maven repos.
- `android/gradle.properties` — 2048MB heap, AndroidX, non-transitive R, official Kotlin style.
- `android/gradle/wrapper/gradle-wrapper.properties` — pinned Gradle 8.14.3.
- `.github/workflows/build.yml` — CI debug build on push/PR to main/develop; signed release + GitHub Release on tag pushes.
- `.github/release-notes.md` — release notes body used by the CI Release step.
- `feature.md` — changelog of resolved issues and roadmap of planned features.
- `README.md` — product behavior, usage instructions, build commands.
- `docs/icon-prompt.md` — launcher icon design brief.

## Dependencies
| Library | Usage | Version |
|---|---|---|
| Jetpack Compose BOM | UI framework | 2024.02.00 |
| Compose Compiler | Kotlin-to-Compose codegen | 1.5.10 |
| Coil (compose + video) | Image loading | 2.5.0 |
| Media3 (exoplayer + ui) | Video playback | 1.2.0 |
| AndroidX Core KTX | Core utilities | 1.12.0 |
| AndroidX Activity Compose | Activity + Compose integration | 1.8.2 |
| AndroidX Lifecycle Runtime KTX | Lifecycle-aware coroutines | 2.7.0 |
| AndroidX DocumentFile | SAF document file wrapper | 1.0.1 |
| AndroidX ExifInterface | EXIF metadata extraction | 1.3.7 |
| Material Icons Extended | Extended icon set | via BOM |

## Runtime/Tooling Preferences
- **Required runtime:** JDK 17 for builds.
- **Build system:** Gradle Kotlin DSL via the wrapper in `android/` (Gradle 8.14.3).
- **Package/dependency resolution:** Google Maven + Maven Central; Gradle Plugin Portal only for plugin resolution. `FAIL_ON_PROJECT_REPOS` is set.
- **Tooling constraints:** AndroidX enabled, non-transitive R classes, UTF-8 file encoding, official Kotlin code style.
- **CI platform:** GitHub Actions, `ubuntu-latest`, with `android-actions/setup-android@v3` for SDK setup.
- **No media permissions in manifest:** SAF-only file access; no `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, or `READ_MEDIA_VIDEO`.
- **ProGuard:** enabled for release builds but `isMinifyEnabled = false`. Rules keep annotations and `com.jp.app.data.**` model classes.

## Testing & QA
- There is no checked-in unit or instrumentation test suite or test framework configuration.
- Current verification is CI-centric: use GitHub Actions (`.github/workflows/build.yml`) for APK build validation instead of local Gradle builds.
- When changing runtime behavior, update only the affected code paths, verify the PR GitHub Actions build, then merge the PR and delete its branch before any release build or final handoff unless explicitly told otherwise.
- For gesture-heavy or UI-logic changes, manual testing on device/emulator is the only verification method.
