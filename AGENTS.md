# Repository Guidelines

## Project Overview
Single-module Android app (Kotlin, Jetpack Compose + Material3) for browsing local images/videos via Storage Access Framework (SAF).
- **License:** AGPL-3.0-only
- **Current version:** beta0.7.10 (versionCode 54)
- **No ViewModel, no DI, no nav component** — all app state lives in a single `MediaBrowserState` object instantiated via `rememberMediaBrowserState(prefs, context)` in `MainActivity.kt` (`MainApp` composable). Screen routing is an `if/else` on `state.isViewing`.
- **No media permissions in manifest** — SAF-only file access. No `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, or `READ_MEDIA_VIDEO`.
- **Material You:** dynamic colors on Android 12+ (`dynamicDarkColorScheme`/`dynamicLightColorScheme`), manual fallback on older versions.

## Tech Stack & Dependencies
All from `android/app/build.gradle.kts` (Compose BOM `2024.02.00`, Kotlin `1.5.10` compiler ext, JVM/compileSdk/targetSdk `34`, minSdk `26`):

- **UI:** `androidx.compose.*` (ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended), `com.google.android.material:material:1.12.0`
- **Platform:** `core-ktx:1.12.0`, `lifecycle-runtime-ktx:2.7.0`, `activity-compose:1.8.2`, `exifinterface:1.3.7`
- **Images:** `io.coil-kt:coil-compose:2.5.0` + `coil-video:2.5.0`
- **Video:** `androidx.media3:media3-exoplayer:1.2.0` + `media3-ui:1.2.0`
- **Tests (not in CI):** `junit:4.13.2`, `robolectric:4.11.1`
- **No `androidx.documentfile:documentfile` dependency** — the SAF scanner uses `DocumentsContract` + `ContentResolver` directly.

## Architecture & Data Flow
`folder URIs (SAF)` → `MediaScanner` recursion on `Dispatchers.IO` (`DocumentsContract` + `ContentResolver.query`) → results serialized to a JSON file (`media_scan_cache.json`) + metadata in `SharedPreferences` via `MediaCache` → shuffled (`ShuffleUtils`) → `MediaViewerScreen`.
- Two screens: `FolderPickerScreen` (home) and `MediaViewerScreen` (viewer).
- `MediaBrowserState` is the single source of truth: it wraps the `MediaScanner` and the cache layer, exposes `var x by mutableStateOf(...)` fields, and orchestrates `loadInitialCache()` / `runScanIfNeeded()`.
- `isFavoriteBrowsing` controls favorites-only mode; `subfolderFilterUri` + `subfolderSortMode` control single-subfolder browsing.
- Cache IO on `Dispatchers.IO`; Compose state via `LaunchedEffect`/`DisposableEffect`.
- Videos use a reused single ExoPlayer instance (Media3) inside `AndroidView(PlayerView)` in `ui/VideoPlayer.kt`. Import alias: `import androidx.media3.common.MediaItem as ExoMediaItem` (avoids name clash with domain `MediaItem`).

## Key Source Files
All under `android/app/src/main/java/com/jp/app/` (package `com.jp.app`):

Root package:
- `MainActivity.kt` — entry point: `ComponentActivity`, Material theme, `MainApp` composable. Calls `rememberMediaBrowserState`, wires `LaunchedEffect`s (`loadInitialCache`, `runScanIfNeeded`), `BackHandler`, and routes to `FolderPickerScreen`/`MediaViewerScreen`. Hosts `ViewerSettingsDialog`.
- `MediaBrowserState.kt` — **the state holder.** `rememberMediaBrowserState(prefs, context)` factory + `class MediaBrowserState(prefs, context, scanner)` holding all `mutableStateOf` fields (folders, respectNomedia, isScanning, scanProgress, mediaItems, isViewing, currentIndex, isFavoriteBrowsing, favoriteUris, subfolderFilterUri, subfolderSortMode, subfolderSortDescending, …) and actions (`rescanMedia`, `toggleFavorite`, `startBrowsing`, `stopScanning`, `clearMediaCacheOnly`, …). Top-level consts: `PREF_SUBFOLDER_SORT_MODE`, `PREF_SUBFOLDER_SORT_DESCENDING`, `ITEM_BATCH_MIN_INTERVAL_MS`.
- `MediaCache.kt` — cache persistence. `MEDIA_CACHE_FILE_NAME`, `PARTIAL_CACHE_MIN_ITEM_DELTA`, `PARTIAL_CACHE_MIN_INTERVAL_MS`; `internal data class CachedMediaScan`; `loadCachedMediaScan` / `saveCachedMediaScan` (atomic `.tmp`+`renameTo`) / `clearCachedMediaScan` / `calculateMediaCacheSizeBytes`. No reflection (manual `JsonReader`/`JsonWriter`).
- `ShuffleUtils.kt` — `reshuffleAvoidingFirst(items, previousFirstUri)` keeps the previously-first item from repeating across sessions.
- `SubfolderSorting.kt` — `enum SubfolderSortMode(FileName|FileSize|Random)` + `List<MediaItem>.sortedBySubfolderOrder(mode, descending)`.

`data/`:
- `data/MediaScanner.kt` — recursive SAF scanner. `scan(folderUris, respectNomedia, initialItems, onProgress): List<MediaItem>` on `Dispatchers.IO`; `data class ScanProgress(...)`; `.nomedia` detection from already-fetched children; `queryChildren` distinguishes `SecurityException` vs generic `Exception` (both logged, return `null` → `failedDirs++`); supports cancellation via `ensureActive()`.
- `data/Models.kt` — `data class MediaItem(uri, name, mimeType, size, folderUri, modifiedAt)` with cached `uriString` and derived `isVideo`/`isImage`. **No `AppSettings`** (removed).

`ui/`:
- `ui/FolderPickerScreen.kt` — home screen: SAF folder auth (`OpenDocumentTree` launcher + `takePersistableUriPermission`), scan/cache controls, about & folder-detail dialogs. Top-level `PROJECT_URL`, `ISSUES_URL`, `DEVELOPER_NAME`; imports `BuildConfig`.
- `ui/FolderPickerComponents.kt` — reusable `ScanStatusComponent` (progress/status text) and `CacheManagement` (cache size + clear buttons) plus their private formatters.
- `ui/FolderPickerUtils.kt` — `folderDisplayName(folderUri)` URI helper.
- `ui/MediaViewerScreen.kt` — full-screen viewer: swipe/zoom gestures, favorite, details dialog, per-video play/pause + mute controls; delegates rendering to private `MediaSurface`.
- `ui/MediaViewerUtils.kt` — pure formatters: `deepestFolderName`, `formatFileSize`, `formatModifiedDate`.
- `ui/VideoPlayer.kt` — self-contained ExoPlayer wrapper: single reused `ExoPlayer` (`REPEAT_MODE_ALL`), `LaunchedEffect(uri)` swaps media, `LaunchedEffect(isMuted)` toggles volume, `DisposableEffect(lifecycleOwner)` pauses/resumes, `DisposableEffect(Unit)` registers error listener + `release()` on dispose.

## Build & Verification
- **Do NOT run local Gradle builds during agent development.** Use GitHub Actions for verification (`.github/workflows/build.yml`).
- Local build if human-requested: `./gradlew assembleDebug` from `android/` directory.
- **验证方式：CI 构建 + 手动真机测试。** 维护者的实际流程是 GitHub Actions 出 APK 后手动安装到自己手机上验证；本应用主要供维护者自用。
- **Robolectric 单元测试存在但不在验证流程内。** `app/src/test/java/com/jp/app/` 下有 `MediaCacheTest`、`ShuffleUtilsTest`、`SubfolderSortingTest`，但 CI 不跑 `test` 任务，agent 也不本地跑。如人工需要：`./gradlew testDebugUnitTest`。
- Debug APK: `android/app/build/outputs/apk/debug/jp-media-viewer-{versionName}-debug.apk`
- Release APK: `android/app/build/outputs/apk/release/jp-media-viewer-{versionName}-release.apk`

## Release Workflow
After every feature/fix, create a full release:
1. PR merges to `main` (verify CI passes first).
2. Bump `versionCode` (+1) and `versionName` in `android/app/build.gradle.kts` and this file.
3. Update `.github/release-notes.md` with changelog.
4. Commit, tag (`git tag betaX.Y.Z`), push commit then tag separately: `git push origin main && git push origin betaX.Y.Z`.
5. Confirm the tag-triggered CI run started (it builds signed release APK + creates GitHub Release automatically). Do not wait for completion unless asked.
- CI reads release body from `.github/release-notes.md`.
- Release signing requires `ANDROID_RELEASE_*` secrets (keystore decoded from `ANDROID_RELEASE_KEYSTORE_BASE64`).

## Commit Convention
```
type(scope): 主题

正文（中文）

由AI生成
Generated by AI
```
Subject and body in Chinese. Footer always `由AI生成` then `Generated by AI`.

## Known Risks & Maintenance Notes

### R8 混淆 (release 构建)
- `android/app/proguard-rules.pro` 已覆盖 Compose runtime、Coil、Media3、data 模型、DocumentsContract。
- 项目使用手动 `JsonReader`/`JsonWriter` 做 JSON 序列化，不依赖类名反射，R8 安全。
- **注意**：添加新第三方库时检查是否需要额外 keep 规则，否则 release APK 可能出现 `ClassNotFoundException`。
- R8 只影响 release 构建，debug 不受影响。

### SAF 扫描器 (直接 ContentResolver 查询)
- 使用 `DocumentsContract.buildChildDocumentsUriUsingTree()` + `ContentResolver.query()`，API 21+，minSdk=26 完全兼容。
- `.nomedia` 检测通过已查询的 children 列表中 `displayName == ".nomedia"` 判断，无需额外查询。
- 部分特殊存储提供器（如国产网盘 DocumentProvider）可能对 projection 列支持不完整，会导致该目录被标记为 `failedDir`。这是提供器自身的局限，非本扫描器的 bug。
- 已移除 `androidx.documentfile:documentfile` 依赖，如需使用 `DocumentFile` API 须重新添加。

### ExoPlayer 实例复用与生命周期
- 单实例通过 `player.setMediaItem()` 切换媒体，不再按 URI 重建；`REPEAT_MODE_ALL`。
- 生命周期感知使用 `DisposableEffect(lifecycleOwner)` + `LifecycleEventObserver`（ON_PAUSE 暂停 / ON_RESUME 播放），`lifecycleOwner` 来自 Compose `LocalLifecycleOwner`。
- 如果未来升级 Compose BOM 到 2024.06+，`LocalLifecycleOwner` 会被标记 deprecated，届时迁移到 `androidx.lifecycle.compose.LocalLifecycleOwner` 并添加 `lifecycle-runtime-compose` 依赖。

### 缓存持久化 (MediaCache)
- 扫描结果以 JSON 数组写入 `context.filesDir/media_scan_cache.json`（`.tmp` + `renameTo` 原子写）。
- `SharedPreferences` 仅存校验元数据（`media_cache_folders`、`media_cache_respect_nomedia`、`media_cache_scanned`、`media_cache_complete`），不再存媒体列表本身（legacy `media_cache_items` 在 save 时清除）。
- `loadCachedMediaScan` 仅在「文件夹集合 key」与「respect_nomedia」都匹配时返回缓存；支持未完成扫描（`complete=false`）以恢复断点续扫。

## Code Review Findings & Outstanding Risks (last reviewed beta0.7.7)
状态图例：✅ 已修复 / ⚠️ 仍待处理（风险）/ ❌ 经复核不成立 / ℹ️ 历史或观察项。

1. ✅ `MediaCache.kt` 死代码 `shouldSavePartialCache` — 已删除。
2. ✅ 状态提取：`MainActivity` 中散落的 `mutableStateOf` 已收敛到 `MediaBrowserState` 类（commit `d12a907`），屏幕路由仍是 `isViewing` 的 `if/else`。
3. ✅ `SubfolderSortMode` 可见性为 `internal`，同 module 内引用正确。
4. ℹ️ 视频播放器提交链曾反复修复编译错误（historical note，质量信号；当前 `VideoPlayer.kt` 经重构已稳定）。
5. ✅ `VideoPlayer.kt` 中 `LaunchedEffect(player)` 多余 key 已移除，改为 `LaunchedEffect(isMuted)`。
6. ✅ `VideoPlayer.kt` 误用 `derivedStateOf` 已改为 `rememberUpdatedState(onLoadError)` / `rememberUpdatedState(onPlayerReady)`。
7. ✅ `MediaScanner.queryChildren` 已区分 `SecurityException` 与泛型 `Exception` 并记录日志；状态层 `runScanIfNeeded` 对 `SecurityException` / `CancellationException` / 泛型异常给出不同提示并清理缓存。
8. ✅ 扫描回调中冗余的 `withContext(Dispatchers.Main)` 切换已移除，改为按 `ITEM_BATCH_MIN_INTERVAL_MS` 批量分发进度。
9. ⚠️ `MediaViewerScreen.kt` 多处硬编码 `Color.Black`/`Color.White`，浏览页不跟随 Material You 动态色调；建议复用 `MaterialTheme.colorScheme.surface` 或提供可配置背景色。
10. ✅ 视频控件触摸区域已扩大到 `IconButton(size=32.dp)`+`Icon(size=20.dp)`。
11. ✅ About 对话框已移除已废弃依赖 "AndroidX DocumentFile"。
12. ✅ `Models.kt` 中死代码 `AppSettings` 已删除，设置由 `MediaBrowserState` 字段 + `SharedPreferences` 承载。
13. ⚠️ README 的「可能会有的功能」与实现不符：视频播放控制（播放/暂停/静音）已实现，但 README 仍列为未做；子文件夹排序已在 beta0.7 实现。发布说明/README 应保持同步。
14. ⚠️ `MediaViewerScreen.kt` 仍承担手势/缩放/滑动预览/视频控制/详情弹窗等，体量偏大；已抽取 `MediaSurface` 与 `MediaViewerUtils` 缓解，但手势逻辑仍建议进一步抽取。
15. ✅ `CachedMediaScan` 已改为 `internal data class`。
16. ⚠️ `VideoPlayer.kt` 的 `DisposableEffect(Unit)` 在 `onDispose` 中 `player.release()`；当前单 Activity + `if/else` 路由下安全，但未来引入 Navigation 组件时需注意 player 重建时序。
17. ✅ **视频播放/暂停键失效已修复**：原播放键点击仅翻转 `isVideoPlaying` 却不驱动 player，且 `onPlaybackStateChanged` 监听器反向覆写该状态。已改为：播放键点击直接 `videoPlayerRef.play()/pause()`，图标状态由 `onIsPlayingChanged` 单向更新（不再用 `onPlaybackStateChanged`——后者在纯暂停/播放切换时不触发）。单向数据流，无状态互相覆写。**待真机确认播放键实际生效。**
18. ✅ **播放器控件三层重叠已修复（B 方案，待真机确认）**：真机复现确认旧版「暂停不了 + 进度条被底部控制栏遮挡」——ExoPlayer 自带控制条的进度条被 App 底部栏视觉遮住，其中间播放键点击被全屏 `pointerInput` 手势层吞掉。改法：`VideoPlayer.kt` 设 `useController = false` 撤掉自带控制条，改由 App 底部控制行自绘：播放/暂停键 + 当前时间 + 可拖动 `Slider` + 总时长 + 静音键。底部行位于 `showControls` 的 `AnimatedVisibility` 内、层级在全屏手势 `Box` 之上，故拖动/点击不再被吞。配套：`MediaViewerScreen` 加位置/时长轮询 `LaunchedEffect`（500ms）、`isSeeking`/`seekPreviewMs` 拖动态、松手 `seekTo` 后立即同步位置防回弹，状态均 `remember(item.uri)` 随切换归零；`MediaViewerUtils.formatVideoTime` 输出 `mm:ss`（未复用 `FolderPickerComponents.formatDuration`，后者是扫描耗时格式，语义不同）。**待真机确认：暂停生效、进度条可拖不被遮、拖动与上下滑切换无手势误触。**
19. ❌ **越界崩溃风险——经复核不成立**：调用方 `MainActivity.kt` 已用 `safeIndex = currentIndex.coerceIn(0, visibleItems.lastIndex)` 传入，且路由条件 `if (state.isViewing && visibleItems.isNotEmpty())` 保证列表非空。`MediaViewerScreen` 内再加 `coerceIn` 属冗余防御，违反「不为不可能场景加错误处理」，不改。
20. ❌ **flush 的 Main 切换是冗余——经复核不成立**：`MediaScanner.scan()` 整个 body 包在 `withContext(Dispatchers.IO)` 内，`onProgress` 在 IO 线程被调用。`flushPendingItems` 里的 `withContext(Dispatchers.Main)` 是安全改 `mutableStateOf` 所**必需**的，正确，不动。（原 finding #8 移除的是扫描回调中另一处真正冗余的切换，二者不同。）
21. ✅ **静音选择已持久化**：新增 `MediaBrowserState.videoMuted`（存 `SharedPreferences` key `video_muted`）+ `saveVideoMuted()`；`MediaViewerScreen` 的本地 `remember` 静音状态改为参数 `isVideoMuted` / `onToggleMute` 由 state holder 承载，退出浏览不再重置。

## User-Facing / UX Backlog（待处理，last reviewed beta0.7.7）
状态图例：✅ 已修复 / ⚠️ 待处理 / ℹ️ 观察项。

1. ◐ **无进度条 / 无法定位——视频部分已修复**：视频已有可拖动进度条（见 finding #18 的 B 方案，待真机确认）。**仍待处理**：图片无"跳到第 N 张"能力，只能逐张划。
2. ℹ️ **上下滑切换与图片缩放手势冲突**：已用 `isImageZoomed` 挡住缩放时的上下滑（逻辑正确），但阈值 `1.01f` 敏感，轻微双指误触即进"缩放态"导致划不动。建议真机试单指快速上滑是否偶尔被误判。
3. ⚠️ **收藏缺失提示无下一步入口**：`startFavoriteBrowsing` 对空收藏 / 收藏文件不在扫描结果中都有清晰中文提示，但用户看到后没有动作入口。可在提示旁加"重新扫描"或"查看收藏列表"按钮。
4. ✅ **双击语义与文案冲突已修复**：`startFavoriteBrowsing` 空收藏提示由"双击即可收藏"改为"浏览时点击底部心形按钮即可收藏",与实际交互一致(双击是缩放,收藏靠心形键)。README 文案本就正确,无需改。
5. ℹ️ **视频默认自动播放且无限循环**：`REPEAT_MODE_ALL` + `playWhenReady = true`，适合随机浏览场景；结合 #21，可考虑同时提供"记住静音选择"。

## Media Model
```kotlin
data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val folderUri: Uri,
    val modifiedAt: Long
) {
    val uriString: String = uri.toString()            // 热路径缓存，避免重复分配
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
```

## Karpathy Coding Guidelines

基于 Andrej Karpathy 的 LLM 编码陷阱观察的编码行为指南。适用于所有非特定领域任务，确保代码简洁、精准、可验证。

### 1. Think Before Coding
**不要假设。不要隐藏困惑。呈现权衡。**
- 在实现之前明确陈述假设；不确定就直接问。
- 存在多个解释时呈现它们，不要默默选一个。
- 存在更简单的方法时指出它，合理时提出反对意见。
- 不清楚就停下、说出困惑、询问。

### 2. Simplicity First
**最小代码解决问题。没有投机性代码。**
- 不要添加用户没要求的功能、抽象、灵活性或可配置性。
- 不要为不可能的场景添加错误处理。
- 如果 200 行可以是 50 行，重写它。

### 3. Surgical Changes
**只碰必须碰的。只清理你自己的烂摊子。**
- 不要"改进"相邻的代码、注释或格式。
- 不要重构没坏的东西；匹配现有风格。
- 注意到无关的死代码时指出它——不要擅自删除。

### 4. Goal-Driven Execution
**定义成功标准。循环直到验证。**
- 本项目的验证方式是 **CI 构建通过 + 手动装真机确认行为**，不是自动化测试。成功标准应表述为可编译、可构建、可在真机上复现预期行为。
- "添加校验" → 明确无效输入的预期行为，改完确认 CI 构建通过、真机正确拦截。
- "修复 bug" → 先在真机复现，改完确认同样场景不再出现。
- "重构 X" → 确认 CI 构建通过，且重构前后真机行为一致。
- `app/src/test/` 下已有的 Robolectric 测试不在此流程内，除非人工明确要求。
