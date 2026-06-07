package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

import com.jp.app.data.MediaItem
import com.jp.app.data.MediaScanner
import com.jp.app.ui.FolderPickerScreen
import com.jp.app.ui.MediaViewerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PREF_MEDIA_CACHE_FOLDERS = "media_cache_folders"
private const val PREF_MEDIA_CACHE_RESPECT_NOMEDIA = "media_cache_respect_nomedia"
private const val PREF_MEDIA_CACHE_SCANNED = "media_cache_scanned"
private const val PREF_MEDIA_CACHE_ITEMS = "media_cache_items"

private data class CachedMediaScan(
    val items: List<MediaItem>,
    val scanned: Int
)

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
    val initialCache = remember { mutableStateOf<CachedMediaScan?>(null) }
    var initialCacheLoaded by remember { mutableStateOf(false) }
    var mediaItems by remember { mutableStateOf(emptyList<MediaItem>()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var mediaLoadError by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<MediaScanner.ScanProgress?>(null) }

    var hasScanned by remember { mutableStateOf(false) }
    var rescanRequest by remember { mutableStateOf(0) }
    var mediaCacheSizeBytes by remember { mutableStateOf(calculateMediaCacheSizeBytes(prefs)) }
    var favoriteUris by remember {
        mutableStateOf(prefs.getStringSet("favorite_uris", emptySet())?.toSet() ?: emptySet())
    }

    LaunchedEffect(folders, respectNomedia) {
        initialCache.value = withContext(Dispatchers.IO) {
            loadCachedMediaScan(prefs, folders, respectNomedia)
        }
        initialCacheLoaded = true
    }
    fun saveFolders(newFolders: List<String>) {
        clearCachedMediaScan(prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
        folders = newFolders
        prefs.edit().putStringSet("folder_uris", newFolders.toSet()).apply()
    }

    fun saveNomedia(value: Boolean) {
        clearCachedMediaScan(prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
        respectNomedia = value
        prefs.edit().putBoolean("respect_nomedia", value).apply()
    }

    fun rescanMedia() {
        clearCachedMediaScan(prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
        rescanRequest++
    }

    fun clearMediaCacheOnly() {
        clearCachedMediaScan(prefs)
        initialCache.value = null
        mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
        scanMessage = "媒体缓存已清除。当前扫描结果仍可继续浏览，需要刷新时请重新扫描。"
    }

    fun clearFavorites() {
        favoriteUris = emptySet()
        prefs.edit().remove("favorite_uris").apply()
        if (isFavoriteBrowsing) {
            isViewing = false
            isFavoriteBrowsing = false
            currentIndex = 0
        }
        scanMessage = "收藏已清空。"
    }

    fun toggleFavorite(item: MediaItem): Set<String> {
        val uriString = item.uri.toString()
        val newFavorites = if (uriString in favoriteUris) {
            favoriteUris - uriString
        } else {
            favoriteUris + uriString
        }
        favoriteUris = newFavorites
        prefs.edit().putStringSet("favorite_uris", newFavorites).apply()
        return newFavorites
    }

    LaunchedEffect(initialCacheLoaded, folders, respectNomedia, rescanRequest) {
        if (!initialCacheLoaded) return@LaunchedEffect
        if (folders.isEmpty()) {
            mediaItems = emptyList()
            currentIndex = 0
            isViewing = false
            isFavoriteBrowsing = false
            isScanning = false
            scanProgress = null

            hasScanned = false
            scanMessage = null
            return@LaunchedEffect
        }

        val cachedScan = initialCache.value
        if (cachedScan != null) {
            mediaItems = cachedScan.items.shuffled()
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

        isScanning = true
        hasScanned = false
        scanMessage = null

        scanProgress = MediaScanner.ScanProgress(scanned = 0, found = 0)
        try {
            var scannedCount = 0
            val items = scanner.scan(folders, respectNomedia) { progress ->
                scannedCount = progress.scanned
                withContext(Dispatchers.Main) {
                    scanProgress = progress
                    if (!isViewing && progress.foundItems.isNotEmpty()) {
                        mediaItems = progress.foundItems
                    }
                }
            }
            if (!isViewing) {
                mediaItems = items.shuffled()
                currentIndex = 0
            }
            withContext(Dispatchers.IO) {
                saveCachedMediaScan(prefs, folders, respectNomedia, items, scannedCount)
            }
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
            hasScanned = true
            if (isFavoriteBrowsing && mediaItems.none { it.uri.toString() in favoriteUris }) {
                isViewing = false
                isFavoriteBrowsing = false
                scanMessage = "收藏文件未在当前扫描结果中找到。可能原因：文件已删除、文件夹未添加，或授权已失效。"
            }
            if (isViewing && mediaItems.isEmpty()) {
                isViewing = false
                isFavoriteBrowsing = false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            isViewing = false
            isFavoriteBrowsing = false
            mediaItems = emptyList()
            hasScanned = true
            clearCachedMediaScan(prefs)
            initialCache.value = null
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
            scanMessage = "无法读取所选文件夹。请删除该文件夹后重新添加授权。"
        } catch (error: Exception) {
            isViewing = false
            isFavoriteBrowsing = false
            mediaItems = emptyList()
            hasScanned = true
            clearCachedMediaScan(prefs)
            initialCache.value = null
            mediaCacheSizeBytes = calculateMediaCacheSizeBytes(prefs)
            scanMessage = "扫描失败：${error.localizedMessage ?: "未知错误"}"
        } finally {
            isScanning = false
        }
    }

    fun startBrowsing() {
        if (mediaItems.isEmpty()) {
            scanMessage = "没有找到图片或视频。可能原因：\n1. 所选目录没有支持的媒体文件\n2. .nomedia 过滤隐藏了部分目录\n3. 文件夹授权已失效，请删除后重新添加"
        } else {
            mediaItems = reshuffleAvoidingFirst(mediaItems, mediaItems.firstOrNull()?.uri?.toString())
            currentIndex = 0
            isFavoriteBrowsing = false
            isViewing = true
        }
    }

    fun startFavoriteBrowsing() {
        val favoriteItems = mediaItems.filter { it.uri.toString() in favoriteUris }
        if (favoriteUris.isEmpty()) {
            scanMessage = "收藏列表为空。浏览图片或视频时双击即可收藏。"
        } else if (favoriteItems.isEmpty()) {
            scanMessage = "收藏文件未在当前扫描结果中找到。可能原因：文件已删除、文件夹未添加，或授权已失效。"
        } else {
            mediaItems = reshuffleAvoidingFirst(mediaItems, favoriteItems.firstOrNull()?.uri?.toString())
            currentIndex = 0
            isFavoriteBrowsing = true
            isViewing = true
        }
    }

    val visibleItems = if (isFavoriteBrowsing) {
        mediaItems.filter { it.uri.toString() in favoriteUris }
    } else {
        mediaItems
    }

    BackHandler(enabled = isViewing) {
        isViewing = false
        isFavoriteBrowsing = false
        showSettings = false
        mediaLoadError = false
    }

    if (isViewing && visibleItems.isNotEmpty()) {
        MediaViewerScreen(
            mediaItems = visibleItems,
            currentIndex = currentIndex.coerceIn(0, visibleItems.lastIndex),
            isFavoriteBrowsing = isFavoriteBrowsing,
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
            isFavorite = visibleItems[currentIndex.coerceIn(0, visibleItems.lastIndex)].uri.toString() in favoriteUris,
            onToggleFavorite = {
                val currentItem = visibleItems[currentIndex.coerceIn(0, visibleItems.lastIndex)]
                val wasFavorite = currentItem.uri.toString() in favoriteUris
                val newFavorites = toggleFavorite(currentItem)
                Toast.makeText(
                    context,
                    if (wasFavorite) "已取消收藏" else "已收藏",
                    Toast.LENGTH_SHORT
                ).show()
                if (isFavoriteBrowsing) {
                    val remainingCount = mediaItems.count { it.uri.toString() in newFavorites }
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
            },
            onSettings = { showSettings = !showSettings },
            onMediaLoadError = {
                mediaLoadError = true
            }
        )

        if (showSettings) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { androidx.compose.material3.Text("设置") },
                text = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Text("遵守 .nomedia", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = respectNomedia,
                            onCheckedChange = { saveNomedia(it) }
                        )
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
            onStartBrowsing = { startBrowsing() },
            onStartFavorites = { startFavoriteBrowsing() },
            isScanning = isScanning,
            scanProgress = scanProgress,

            mediaCount = mediaItems.size,
            favoriteCount = favoriteUris.size,
            hasScanned = hasScanned,
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

private fun loadCachedMediaScan(
    prefs: SharedPreferences,
    folders: List<String>,
    respectNomedia: Boolean
): CachedMediaScan? {
    if (prefs.getString(PREF_MEDIA_CACHE_FOLDERS, null) != mediaCacheFoldersKey(folders)) return null
    if (prefs.getBoolean(PREF_MEDIA_CACHE_RESPECT_NOMEDIA, !respectNomedia) != respectNomedia) return null

    val cacheJson = prefs.getString(PREF_MEDIA_CACHE_ITEMS, null) ?: return null
    return runCatching {
        val itemsJson = JSONArray(cacheJson)
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val itemJson = itemsJson.getJSONObject(index)
                add(
                    MediaItem(
                        uri = Uri.parse(itemJson.getString("uri")),
                        name = itemJson.getString("name"),
                        mimeType = itemJson.getString("mimeType"),
                        size = itemJson.getLong("size"),
                        folderUri = Uri.parse(itemJson.getString("folderUri")),
                        modifiedAt = itemJson.optLong("modifiedAt", 0L)
                    )
                )
            }
        }
        CachedMediaScan(
            items = items,
            scanned = prefs.getInt(PREF_MEDIA_CACHE_SCANNED, items.size)
        )
    }.getOrNull()
}

private fun saveCachedMediaScan(
    prefs: SharedPreferences,
    folders: List<String>,
    respectNomedia: Boolean,
    items: List<MediaItem>,
    scanned: Int
) {
    val itemsJson = JSONArray()
    items.forEach { item ->
        itemsJson.put(
            JSONObject()
                .put("uri", item.uri.toString())
                .put("name", item.name)
                .put("mimeType", item.mimeType)
                .put("size", item.size)
                .put("folderUri", item.folderUri.toString())
                .put("modifiedAt", item.modifiedAt)
        )
    }

    prefs.edit()
        .putString(PREF_MEDIA_CACHE_FOLDERS, mediaCacheFoldersKey(folders))
        .putBoolean(PREF_MEDIA_CACHE_RESPECT_NOMEDIA, respectNomedia)
        .putInt(PREF_MEDIA_CACHE_SCANNED, scanned)
        .putString(PREF_MEDIA_CACHE_ITEMS, itemsJson.toString())
        .apply()
}

private fun clearCachedMediaScan(prefs: SharedPreferences) {
    prefs.edit()
        .remove(PREF_MEDIA_CACHE_FOLDERS)
        .remove(PREF_MEDIA_CACHE_RESPECT_NOMEDIA)
        .remove(PREF_MEDIA_CACHE_SCANNED)
        .remove(PREF_MEDIA_CACHE_ITEMS)
        .apply()
}

private fun calculateMediaCacheSizeBytes(prefs: SharedPreferences): Long {
    return (prefs.getString(PREF_MEDIA_CACHE_ITEMS, null)?.length ?: 0).toLong() +
        (prefs.getString(PREF_MEDIA_CACHE_FOLDERS, null)?.length ?: 0).toLong()
}


private fun reshuffleAvoidingFirst(items: List<MediaItem>, previousFirstUri: String?): List<MediaItem> {
    if (items.size < 2 || previousFirstUri == null) return items.shuffled()

    val shuffled = items.shuffled().toMutableList()
    if (shuffled.firstOrNull()?.uri?.toString() != previousFirstUri) return shuffled

    val swapIndex = shuffled.indexOfFirst { it.uri.toString() != previousFirstUri }
    if (swapIndex > 0) {
        val first = shuffled[0]
        shuffled[0] = shuffled[swapIndex]
        shuffled[swapIndex] = first
    }
    return shuffled
}

private fun mediaCacheFoldersKey(folders: List<String>): String {
    return folders.sorted().joinToString("\n")
}