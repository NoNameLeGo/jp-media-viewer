package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonWriter
import android.widget.Toast
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.jp.app.data.MediaItem
import com.jp.app.data.MediaScanner
import com.jp.app.loadCachedMediaScan
import com.jp.app.saveCachedMediaScan
import com.jp.app.clearCachedMediaScan
import com.jp.app.calculateMediaCacheSizeBytes
import com.jp.app.PARTIAL_CACHE_MIN_ITEM_DELTA
import com.jp.app.PARTIAL_CACHE_MIN_INTERVAL_MS
import com.jp.app.reshuffleAvoidingFirst
import com.jp.app.sortedBySubfolderOrder
import com.jp.app.ui.FolderPickerScreen
import com.jp.app.ui.MediaViewerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.Locale

private const val PREF_SUBFOLDER_SORT_MODE = "subfolder_sort_mode"
private const val PREF_SUBFOLDER_SORT_DESCENDING = "subfolder_sort_descending"

private enum class SubfolderSortMode(val prefValue: String, val label: String) {
    FileName("file_name", "文件名"),
    FileSize("file_size", "文件大小"),
    Random("random", "随机");

    companion object {
        fun fromPref(value: String?): SubfolderSortMode {
            return entries.firstOrNull { it.prefValue == value } ?: FileName
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme) {
                dynamicDarkColorScheme(this)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(this)
            } else if (darkTheme) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainApp(prefs, applicationContext)
                }
            }
        }
    }
}

@Composable
private fun MainApp(prefs: SharedPreferences, context: Context) {
    val scanner = remember { MediaScanner(context) }

    var folders by remember {
        mutableStateOf(
            prefs.getStringSet("folder_uris", emptySet())?.toList() ?: emptyList()
        )
    }
    var respectNomedia by remember { mutableStateOf(prefs.getBoolean("respect_nomedia", true)) }
    var isScanning by remember { mutableStateOf(false) }
    var isViewing by remember { mutableStateOf(false) }
    var isFavoriteBrowsing by remember { mutableStateOf(false) }
    var subfolderFilterUri by remember { mutableStateOf<Uri?>(null) }
    val initialCache = remember { mutableStateOf<CachedMediaScan?>(null) }
    var initialCacheLoaded by remember { mutableStateOf(false) }
    var mediaItems by remember { mutableStateOf(emptyList<MediaItem>()) }
    var latestScannedItems by remember { mutableStateOf(emptyList<MediaItem>()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var mediaLoadError by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<MediaScanner.ScanProgress?>(null) }
    var activeScanJob by remember { mutableStateOf<Job?>(null) }
    var stopScanRequested by remember { mutableStateOf(false) }

    var hasScanned by remember { mutableStateOf(false) }
    var rescanRequest by remember { mutableStateOf(0) }
    var mediaCacheSizeBytes by remember { mutableStateOf(calculateMediaCacheSizeBytes(context, prefs)) }
    var subfolderSortMode by remember {
        mutableStateOf(SubfolderSortMode.fromPref(prefs.getString(PREF_SUBFOLDER_SORT_MODE, null)))
    }
    var subfolderSortDescending by remember {
        mutableStateOf(prefs.getBoolean(PREF_SUBFOLDER_SORT_DESCENDING, false))
    }
    var favoriteUris by remember {
        mutableStateOf(prefs.getStringSet("favorite_uris", emptySet())?.toSet() ?: emptySet())
    }

    LaunchedEffect(folders, respectNomedia) {
        initialCache.value = withContext(Dispatchers.IO) {
            loadCachedMediaScan(context, prefs, folders, respectNomedia)
        }
        initialCacheLoaded = true
    }
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

    LaunchedEffect(initialCacheLoaded, folders, respectNomedia, rescanRequest) {
        if (!initialCacheLoaded) return@LaunchedEffect
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
            return@LaunchedEffect
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
            return@LaunchedEffect
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
            activeScanJob = currentCoroutineContext()[Job]
            var scannedCount = 0
            var lastPartialCacheItemCount = cachedScan?.items?.size ?: 0
            var lastPartialCacheSavedAt = SystemClock.elapsedRealtime()
            val items = scanner.scan(
                folderUris = folders,
                respectNomedia = respectNomedia,
                initialItems = cachedScan?.items.orEmpty()
            ) { progress ->
                scannedCount = progress.scanned
                val foundItems = progress.foundItems
                withContext(Dispatchers.Main) {
                    scanProgress = progress.copy(foundItems = emptyList())
                    if (foundItems.isNotEmpty()) {
                        latestScannedItems = foundItems
                    }
                    if (!isViewing && foundItems.isNotEmpty()) {
                        mediaItems = foundItems
                    }
                }
                val now = SystemClock.elapsedRealtime()
                val itemDelta = foundItems.size - lastPartialCacheItemCount
                val shouldSavePartialCache = itemDelta > 0 &&
                    (itemDelta >= PARTIAL_CACHE_MIN_ITEM_DELTA || now - lastPartialCacheSavedAt >= PARTIAL_CACHE_MIN_INTERVAL_MS)
                if (shouldSavePartialCache) {
                    runCatching {
                        saveCachedMediaScan(context, prefs, folders, respectNomedia, foundItems, progress.scanned, complete = false)
                    }.onSuccess {
                        lastPartialCacheItemCount = foundItems.size
                        lastPartialCacheSavedAt = now
                        withContext(Dispatchers.Main) {
                            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(context, prefs)
                        }
                    }
                }
            }
            if (!isViewing) {
                mediaItems = items.shuffled()
                currentIndex = 0
            }
            latestScannedItems = items
            withContext(Dispatchers.IO) {
                runCatching {
                    saveCachedMediaScan(context, prefs, folders, respectNomedia, items, scannedCount, complete = true)
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
                    saveCachedMediaScan(context, prefs, folders, respectNomedia, pausedItems, scanProgress?.scanned ?: 0, complete = false)
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

    fun stopScanning() {
        activeScanJob?.let { job ->
            stopScanRequested = true
            job.cancel()
        }
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
            scanMessage = "收藏列表为空。浏览图片或视频时双击即可收藏。"
        } else if (favoriteItems.isEmpty()) {
            scanMessage = "收藏文件未在当前扫描结果中找到。可能原因：文件已删除、文件夹未添加，或授权已失效。"
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

    val visibleItems by remember {
        derivedStateOf {
            when {
                isFavoriteBrowsing -> mediaItems.filter { it.uriString in favoriteUris }
                subfolderFilterUri != null -> mediaItems
                    .filter { it.folderUri == subfolderFilterUri }
                    .sortedBySubfolderOrder(subfolderSortMode, subfolderSortDescending)
                else -> mediaItems
            }
        }
    }

    fun toggleSubfolderFilter() {
        if (visibleItems.isEmpty()) return
        val currentItem = visibleItems[currentIndex.coerceIn(0, visibleItems.lastIndex)]
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

    BackHandler(enabled = isViewing) {
        isViewing = false
        isFavoriteBrowsing = false
        showSettings = false
        mediaLoadError = false
        subfolderFilterUri = null
    }

    if (isViewing && visibleItems.isNotEmpty()) {
        MediaViewerScreen(
            mediaItems = visibleItems,
            currentIndex = currentIndex.coerceIn(0, visibleItems.lastIndex),
            isFavoriteBrowsing = isFavoriteBrowsing,
            subfolderFilterUri = subfolderFilterUri,
            onNext = {
                if (visibleItems.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % visibleItems.size
                }
            },
            onPrevious = {
                if (visibleItems.isNotEmpty()) {
                    currentIndex = (currentIndex - 1 + visibleItems.size) % visibleItems.size
                }
            },
            isFavorite = visibleItems[currentIndex.coerceIn(0, visibleItems.lastIndex)].uriString in favoriteUris,
            onToggleFavorite = {
                val currentItem = visibleItems[currentIndex.coerceIn(0, visibleItems.lastIndex)]
                val wasFavorite = currentItem.uriString in favoriteUris
                val newFavorites = toggleFavorite(currentItem)
                Toast.makeText(
                    context,
                    if (wasFavorite) "已取消收藏" else "已收藏",
                    Toast.LENGTH_SHORT
                ).show()
                if (isFavoriteBrowsing) {
                    val remainingCount = mediaItems.count { it.uriString in newFavorites }
                    if (remainingCount == 0) {
                        isViewing = false
                        isFavoriteBrowsing = false
                        currentIndex = 0
                        scanMessage = "收藏列表为空。"
                    } else if (currentIndex >= remainingCount) {
                        currentIndex = remainingCount - 1
                    }
                }
            },
            onBack = {
                isViewing = false
                isFavoriteBrowsing = false
                subfolderFilterUri = null
            },
            onSettings = { showSettings = !showSettings },
            onToggleSubfolderFilter = { toggleSubfolderFilter() },
            onMediaLoadError = {
                mediaLoadError = true
            }
        )

        if (showSettings) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { androidx.compose.material3.Text("设置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Text("遵守 .nomedia", modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(
                                checked = respectNomedia,
                                onCheckedChange = { saveNomedia(it) }
                            )
                        }

                        androidx.compose.material3.Text("子文件夹模式排序")
                        SubfolderSortMode.entries.forEach { mode ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = subfolderSortMode == mode,
                                    onClick = { saveSubfolderSortMode(mode) }
                                )
                                androidx.compose.material3.Text(mode.label)
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Text("逆序排列", modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(
                                checked = subfolderSortMode != SubfolderSortMode.Random && subfolderSortDescending,
                                onCheckedChange = { saveSubfolderSortDescending(it) },
                                enabled = subfolderSortMode != SubfolderSortMode.Random
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showSettings = false
                    }) {
                        androidx.compose.material3.Text("关闭")
                    }
                }
            )
        }
    } else {
        FolderPickerScreen(
            folders = folders,
            respectNomedia = respectNomedia,
            onFoldersChanged = { saveFolders(it) },
            onRespectNomediaChanged = { saveNomedia(it) },
            onRescan = { rescanMedia() },
            onStopScan = { stopScanning() },
            onStartBrowsing = { startBrowsing() },
            onStartFavorites = { startFavoriteBrowsing() },
            isScanning = isScanning,
            scanProgress = scanProgress,

            mediaCount = mediaItems.size,
            favoriteCount = favoriteUris.size,
            hasScanned = hasScanned,
            canResumeScan = !hasScanned && mediaItems.isNotEmpty(),
            mediaCacheSizeBytes = mediaCacheSizeBytes,
            onClearMediaCache = { clearMediaCacheOnly() },
            onClearFavorites = { clearFavorites() }
        )
    }

    if (mediaLoadError) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { mediaLoadError = false },
            title = { androidx.compose.material3.Text("媒体读取失败") },
            text = { androidx.compose.material3.Text("文件可能已删除、移动或授权已失效。请重新扫描刷新媒体列表。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    mediaLoadError = false
                    isViewing = false
                    isFavoriteBrowsing = false
                    subfolderFilterUri = null
                    rescanMedia()
                }) {
                    androidx.compose.material3.Text("重新扫描")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { mediaLoadError = false }) {
                    androidx.compose.material3.Text("稍后处理")
                }
            }
        )
    }

    if (scanMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { scanMessage = null },
            title = { androidx.compose.material3.Text("扫描提示") },
            text = { androidx.compose.material3.Text(scanMessage ?: "") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { scanMessage = null }) {
                    androidx.compose.material3.Text("知道了")
                }
            }
        )
    }
}
}
