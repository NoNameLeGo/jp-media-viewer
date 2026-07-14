package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jp.app.data.MediaItem
import com.jp.app.data.MediaScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val PREF_SUBFOLDER_SORT_MODE = "subfolder_sort_mode"
private const val PREF_SUBFOLDER_SORT_DESCENDING = "subfolder_sort_descending"
private const val ITEM_BATCH_MIN_INTERVAL_MS = 300L

enum class FavoriteHintAction { StartBrowsing, Rescan }

data class FavoriteHint(val message: String, val action: FavoriteHintAction)

enum class SubfolderSortMode(val prefValue: String, val label: String) {
    FileName("file_name", "文件名"),
    FileSize("file_size", "文件大小"),
    Random("random", "随机");

    companion object {
        fun fromPref(value: String?): SubfolderSortMode {
            return entries.firstOrNull { it.prefValue == value } ?: FileName
        }
    }
}

/**
 * Composable state holder for all media browsing state and business logic.
 * Instantiate via [rememberMediaBrowserState] inside a Composable.
 */
@Composable
fun rememberMediaBrowserState(
    prefs: SharedPreferences,
    context: Context
): MediaBrowserState {
    val scanner = remember { MediaScanner(context) }
    return remember { MediaBrowserState(prefs, context, scanner) }
}

class MediaBrowserState(
    private val prefs: SharedPreferences,
    private val context: Context,
    private val scanner: MediaScanner
) {
    // ── Core state ──────────────────────────────────────────────

    var folders by mutableStateOf(
        prefs.getStringSet("folder_uris", emptySet())?.toList() ?: emptyList()
    )
    var respectNomedia by mutableStateOf(prefs.getBoolean("respect_nomedia", true))

    // ── Scan state ──────────────────────────────────────────────

    var isScanning by mutableStateOf(false)
    private val initialCache = mutableStateOf<CachedMediaScan?>(null)
    internal var initialCacheLoaded by mutableStateOf(false)
    var hasScanned by mutableStateOf(false)
    var scanProgress by mutableStateOf<MediaScanner.ScanProgress?>(null)
    var scanMessage by mutableStateOf<String?>(null)
    var mediaCacheSizeBytes by mutableStateOf(calculateMediaCacheSizeBytes(context, prefs))
    private var activeScanJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    private var stopScanRequested by mutableStateOf(false)
    internal var rescanRequest by mutableStateOf(0)

    // ── Content ─────────────────────────────────────────────────

    var mediaItems by mutableStateOf(emptyList<MediaItem>())
    private var latestScannedItems by mutableStateOf(emptyList<MediaItem>())

    // ── UI / navigation ─────────────────────────────────────────

    var isViewing by mutableStateOf(false)
    var currentIndex by mutableStateOf(0)
    var showSettings by mutableStateOf(false)
    var mediaLoadError by mutableStateOf(false)

    // ── Favorites ───────────────────────────────────────────────

    var isFavoriteBrowsing by mutableStateOf(false)
    var favoriteUris by mutableStateOf(
        prefs.getStringSet("favorite_uris", emptySet())?.toSet() ?: emptySet()
    )
    var favoriteHint by mutableStateOf<FavoriteHint?>(null)

    // ── Subfolder ───────────────────────────────────────────────

    var subfolderFilterUri by mutableStateOf<Uri?>(null)
    var subfolderSortMode by mutableStateOf(
        SubfolderSortMode.fromPref(prefs.getString(PREF_SUBFOLDER_SORT_MODE, null))
    )
    var subfolderSortDescending by mutableStateOf(
        prefs.getBoolean(PREF_SUBFOLDER_SORT_DESCENDING, false)
    )

    // ── Video ───────────────────────────────────────────────────

    var videoMuted by mutableStateOf(prefs.getBoolean("video_muted", false))

    // ── Derived ─────────────────────────────────────────────────

    val visibleItems: List<MediaItem>
        get() = when {
            isFavoriteBrowsing -> mediaItems.filter { it.uriString in favoriteUris }
            subfolderFilterUri != null -> mediaItems
                .filter { it.folderUri == subfolderFilterUri }
                .sortedBySubfolderOrder(subfolderSortMode, subfolderSortDescending)
            else -> mediaItems
        }

    // ── Effects (call from LaunchedEffect in composable) ────────

    suspend fun loadInitialCache() {
        initialCache.value = withContext(Dispatchers.IO) {
            loadCachedMediaScan(context, prefs, folders, respectNomedia)
        }
        initialCacheLoaded = true
    }

    @Suppress("NAME_SHADOWING")
    suspend fun runScanIfNeeded() {
        if (!initialCacheLoaded) return
        if (folders.isEmpty()) {
            mediaItems = emptyList()
            latestScannedItems = emptyList()
            currentIndex = 0
            isViewing = false
            isFavoriteBrowsing = false
            subfolderFilterUri = null
            isScanning = false
            scanProgress = null
            hasScanned = false
            scanMessage = null
            return
        }

        val cachedScan = initialCache.value
        if (cachedScan?.complete == true) {
            mediaItems = cachedScan.items.shuffled()
            latestScannedItems = cachedScan.items
            currentIndex = 0
            hasScanned = true
            isScanning = false
            scanProgress = MediaScanner.ScanProgress(
                scanned = cachedScan.scanned,
                found = cachedScan.items.size
            )
            scanMessage = null
            return
        }

        if (cachedScan != null) {
            mediaItems = cachedScan.items
            latestScannedItems = cachedScan.items
            currentIndex = 0
            scanProgress = MediaScanner.ScanProgress(
                scanned = cachedScan.scanned,
                found = cachedScan.items.size
            )
        } else {
            scanProgress = MediaScanner.ScanProgress(scanned = 0, found = 0)
        }

        isScanning = true
        hasScanned = false
        scanMessage = null
        try {
            stopScanRequested = false
            activeScanJob = currentCoroutineContext()[kotlinx.coroutines.Job]
            var scannedCount = 0
            var lastPartialCacheItemCount = cachedScan?.items?.size ?: 0
            var lastPartialCacheSavedAt = SystemClock.elapsedRealtime()
            var lastItemDispatchAt = 0L
            var pendingBatchItems: List<MediaItem> = emptyList()

            suspend fun flushPendingItems(progress: MediaScanner.ScanProgress) {
                if (pendingBatchItems.isEmpty()) return
                withContext(Dispatchers.Main) {
                    scanProgress = progress.copy(foundItems = emptyList())
                    latestScannedItems = pendingBatchItems
                    if (!isViewing) {
                        mediaItems = pendingBatchItems
                    }
                }
                pendingBatchItems = emptyList()
                lastItemDispatchAt = SystemClock.elapsedRealtime()
            }

            val items = scanner.scan(
                folderUris = folders,
                respectNomedia = respectNomedia,
                initialItems = cachedScan?.items.orEmpty()
            ) { progress ->
                scannedCount = progress.scanned
                val foundItems = progress.foundItems
                val now = SystemClock.elapsedRealtime()

                if (foundItems.isNotEmpty()) {
                    pendingBatchItems = foundItems
                }

                val shouldDispatch = foundItems.isNotEmpty() &&
                    (now - lastItemDispatchAt >= ITEM_BATCH_MIN_INTERVAL_MS)

                if (shouldDispatch) {
                    flushPendingItems(progress)
                }

                val itemDelta = foundItems.size - lastPartialCacheItemCount
                val shouldSave = itemDelta > 0 &&
                    (itemDelta >= PARTIAL_CACHE_MIN_ITEM_DELTA ||
                        now - lastPartialCacheSavedAt >= PARTIAL_CACHE_MIN_INTERVAL_MS)
                if (shouldSave) {
                    runCatching {
                        saveCachedMediaScan(
                            context, prefs, folders, respectNomedia,
                            foundItems, progress.scanned, complete = false
                        )
                    }.onSuccess {
                        lastPartialCacheItemCount = foundItems.size
                        lastPartialCacheSavedAt = now
                    }
                }
            }
            flushPendingItems(
                MediaScanner.ScanProgress(
                    scanned = scannedCount,
                    found = items.size,
                    foundItems = items
                )
            )
            if (!isViewing) {
                mediaItems = items.shuffled()
                currentIndex = 0
            }
            latestScannedItems = items
            withContext(Dispatchers.IO) {
                runCatching {
                    saveCachedMediaScan(
                        context, prefs, folders, respectNomedia,
                        items, scannedCount, complete = true
                    )
                }
            }
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
            hasScanned = true
            if (isFavoriteBrowsing && mediaItems.none { it.uriString in favoriteUris }) {
                isViewing = false
                isFavoriteBrowsing = false
                scanMessage = "收藏文件未在当前扫描结果中找到。可能原因：文件已删除、文件夹未添加，或授权已失效。"
                subfolderFilterUri = null
            }
            if (isViewing && mediaItems.isEmpty()) {
                isViewing = false
                isFavoriteBrowsing = false
                subfolderFilterUri = null
            }
        } catch (error: CancellationException) {
            if (!stopScanRequested) throw error
            val pausedItems = latestScannedItems.takeIf { it.isNotEmpty() } ?: mediaItems
            withContext(Dispatchers.IO) {
                runCatching {
                    saveCachedMediaScan(
                        context, prefs, folders, respectNomedia,
                        pausedItems, scanProgress?.scanned ?: 0, complete = false
                    )
                }
            }
            if (!isViewing) {
                mediaItems = pausedItems
            }
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
            if (pausedItems.isNotEmpty()) {
                hasScanned = false
                scanMessage = "扫描已暂停，进度已保存。可先行浏览已找到的 ${pausedItems.size} 个媒体，继续补扫请点击「继续补扫」。"
            }
        } catch (_: SecurityException) {
            isViewing = false
            isFavoriteBrowsing = false
            subfolderFilterUri = null
            mediaItems = emptyList()
            hasScanned = true
            clearCachedMediaScan(context, prefs)
            initialCache.value = null
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
            scanMessage = "无法读取所选文件夹。请删除该文件夹后重新添加授权。"
        } catch (error: Exception) {
            isViewing = false
            isFavoriteBrowsing = false
            subfolderFilterUri = null
            mediaItems = emptyList()
            hasScanned = true
            clearCachedMediaScan(context, prefs)
            initialCache.value = null
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
            scanMessage = "扫描失败：${error.localizedMessage ?: "未知错误"}"
        } finally {
            isScanning = false
            activeScanJob = null
            stopScanRequested = false
        }
    }

    // ── Actions ─────────────────────────────────────────────────

    fun saveFolders(newFolders: List<String>) {
        clearCachedMediaScan(context, prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
        folders = newFolders
        prefs.edit().putStringSet("folder_uris", newFolders.toSet()).apply()
        subfolderFilterUri = null
    }

    fun saveNomedia(value: Boolean) {
        clearCachedMediaScan(context, prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
        respectNomedia = value
        prefs.edit().putBoolean("respect_nomedia", value).apply()
        subfolderFilterUri = null
    }

    fun saveSubfolderSortMode(value: SubfolderSortMode) {
        subfolderSortMode = value
        prefs.edit().putString(PREF_SUBFOLDER_SORT_MODE, value.prefValue).apply()
        if (subfolderFilterUri != null) currentIndex = 0
    }

    fun saveSubfolderSortDescending(value: Boolean) {
        subfolderSortDescending = value
        prefs.edit().putBoolean(PREF_SUBFOLDER_SORT_DESCENDING, value).apply()
        if (subfolderFilterUri != null) currentIndex = 0
    }

    fun saveVideoMuted(value: Boolean) {
        videoMuted = value
        prefs.edit().putBoolean("video_muted", value).apply()
    }

    fun rescanMedia() {
        if (!hasScanned && mediaItems.isNotEmpty()) {
            val resumableItems = latestScannedItems.takeIf { it.isNotEmpty() } ?: mediaItems
            initialCache.value = CachedMediaScan(
                items = resumableItems,
                scanned = scanProgress?.scanned ?: 0,
                complete = false
            )
            mediaItems = resumableItems
        } else {
            clearCachedMediaScan(context, prefs)
            initialCache.value = null
            mediaItems = emptyList()
            latestScannedItems = emptyList()
            scanProgress = MediaScanner.ScanProgress(scanned = 0, found = 0)
        }
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
        rescanRequest++
        subfolderFilterUri = null
    }

    fun clearMediaCacheOnly() {
        clearCachedMediaScan(context, prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
        scanMessage = "媒体缓存已清除。当前扫描结果仍可继续浏览，需要刷新时请重新扫描。"
    }

    fun clearFavorites() {
        favoriteUris = emptySet()
        prefs.edit().remove("favorite_uris").apply()
        if (isFavoriteBrowsing) {
            isViewing = false
            isFavoriteBrowsing = false
            currentIndex = 0
            subfolderFilterUri = null
        }
        scanMessage = "收藏已清空。"
    }

    fun toggleFavorite(item: MediaItem): Set<String> {
        val newFavorites = if (item.uriString in favoriteUris) {
            favoriteUris - item.uriString
        } else {
            favoriteUris + item.uriString
        }
        favoriteUris = newFavorites
        prefs.edit().putStringSet("favorite_uris", newFavorites).apply()
        return newFavorites
    }

    fun startBrowsing() {
        if (mediaItems.isEmpty()) {
            scanMessage = "没有找到图片或视频。可能原因：\n1. 所选目录没有支持的媒体文件\n2. .nomedia 过滤隐藏了部分目录\n3. 文件夹授权已失效，请删除后重新添加"
        } else {
            mediaItems = reshuffleAvoidingFirst(mediaItems, mediaItems.firstOrNull()?.uri?.toString())
            currentIndex = 0
            isFavoriteBrowsing = false
            subfolderFilterUri = null
            isViewing = true
        }
    }

    fun startFavoriteBrowsing() {
        val favoriteItems = mediaItems.filter { it.uriString in favoriteUris }
        if (favoriteUris.isEmpty()) {
            favoriteHint = FavoriteHint(
                "收藏列表为空。浏览图片或视频时点击底部的心形按钮即可收藏。",
                FavoriteHintAction.StartBrowsing
            )
        } else if (favoriteItems.isEmpty()) {
            favoriteHint = FavoriteHint(
                "收藏文件未在当前扫描结果中找到。可能原因：文件已删除、文件夹未添加，或授权已失效。",
                FavoriteHintAction.Rescan
            )
        } else {
            val shuffledFavorites = favoriteItems.shuffled()
            val favoriteUriSet = shuffledFavorites.map { it.uriString }.toSet()
            mediaItems = shuffledFavorites + mediaItems.filter { it.uriString !in favoriteUriSet }
            currentIndex = 0
            isFavoriteBrowsing = true
            subfolderFilterUri = null
            isViewing = true
        }
    }

    fun toggleSubfolderFilter() {
        val items = visibleItems
        if (items.isEmpty()) return
        val currentItem = items[currentIndex.coerceIn(0, items.lastIndex)]
        val nextFilterUri = if (subfolderFilterUri == null) currentItem.folderUri else null
        val nextItems = if (nextFilterUri == null) {
            mediaItems
        } else {
            mediaItems
                .filter { it.folderUri == nextFilterUri }
                .sortedBySubfolderOrder(subfolderSortMode, subfolderSortDescending)
        }
        val nextIndex = if (nextFilterUri == null) {
            nextItems.indexOfFirst { it.uri == currentItem.uri }.coerceAtLeast(0)
        } else {
            0
        }
        subfolderFilterUri = nextFilterUri
        isFavoriteBrowsing = false
        currentIndex = nextIndex.coerceAtLeast(0)
    }

    fun stopScanning() {
        activeScanJob?.let { job ->
            stopScanRequested = true
            job.cancel()
        }
    }
}
