package com.jp.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jp.app.ui.FolderPickerScreen
import com.jp.app.ui.MediaViewerScreen
import com.jp.app.ui.SettingsScreen
import com.jp.app.ui.theme.JpMediaViewerTheme

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainApp(prefs, applicationContext)
        }
    }
}

@Composable
private fun MainApp(prefs: android.content.SharedPreferences, context: android.content.Context) {
    val state = rememberMediaBrowserState(prefs, context)
    val contextForToast = LocalContext.current

    JpMediaViewerTheme(pureBlack = state.pureBlack) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainAppContent(state, contextForToast)
        }
    }
}

@Composable
private fun MainAppContent(state: MediaBrowserState, contextForToast: android.content.Context) {
    // ── Effects ──────────────────────────────────────────────
    androidx.compose.runtime.LaunchedEffect(state.folders, state.respectNomedia) {
        state.loadInitialCache()
    }
    androidx.compose.runtime.LaunchedEffect(state.initialCacheLoaded, state.folders, state.respectNomedia, state.rescanRequest) {
        state.runScanIfNeeded()
    }

    // ── Back ─────────────────────────────────────────────────
    BackHandler(enabled = state.isViewing || state.showAppSettings) {
        if (state.showAppSettings) {
            state.showAppSettings = false
        } else {
            state.isViewing = false
            state.isFavoriteBrowsing = false
            state.showSettings = false
            state.mediaLoadError = false
            state.subfolderFilterUri = null
        }
    }

    // ── Routing ──────────────────────────────────────────────
    val visibleItems = state.visibleItems

    if (state.isViewing && visibleItems.isNotEmpty()) {
        val safeIndex = state.currentIndex.coerceIn(0, visibleItems.lastIndex)

        MediaViewerScreen(
            mediaItems = visibleItems,
            currentIndex = safeIndex,
            isFavoriteBrowsing = state.isFavoriteBrowsing,
            subfolderFilterUri = state.subfolderFilterUri,
            onNext = {
                if (visibleItems.isNotEmpty()) {
                    state.currentIndex = (state.currentIndex + 1) % visibleItems.size
                }
            },
            onPrevious = {
                if (visibleItems.isNotEmpty()) {
                    state.currentIndex = (state.currentIndex - 1 + visibleItems.size) % visibleItems.size
                }
            },
            onJumpTo = { index ->
                if (visibleItems.isNotEmpty()) {
                    state.currentIndex = index.coerceIn(0, visibleItems.lastIndex)
                }
            },
            isFavorite = visibleItems[safeIndex].uriString in state.favoriteUris,
            onToggleFavorite = {
                val currentItem = visibleItems[state.currentIndex.coerceIn(0, visibleItems.lastIndex)]
                val wasFavorite = currentItem.uriString in state.favoriteUris
                val newFavorites = state.toggleFavorite(currentItem)
                Toast.makeText(
                    contextForToast,
                    if (wasFavorite) "已取消收藏" else "已收藏",
                    Toast.LENGTH_SHORT
                ).show()
                if (state.isFavoriteBrowsing) {
                    val remainingCount = state.mediaItems.count { it.uriString in newFavorites }
                    if (remainingCount == 0) {
                        state.isViewing = false
                        state.isFavoriteBrowsing = false
                        state.currentIndex = 0
                        state.scanMessage = "收藏列表为空。"
                    } else if (state.currentIndex >= remainingCount) {
                        state.currentIndex = remainingCount - 1
                    }
                }
            },
            onBack = {
                state.isViewing = false
                state.isFavoriteBrowsing = false
                state.subfolderFilterUri = null
            },
            onSettings = { state.showSettings = !state.showSettings },
            onToggleSubfolderFilter = { state.toggleSubfolderFilter() },
            onMediaLoadError = { state.mediaLoadError = true },
            isVideoMuted = state.videoMuted,
            onToggleMute = { state.saveVideoMuted(!state.videoMuted) }
        )

        // ── Viewer settings dialog ─────────────────────────────
        if (state.showSettings) {
            ViewerSettingsDialog(state)
        }
    } else if (state.showAppSettings) {
        SettingsScreen(
            respectNomedia = state.respectNomedia,
            onRespectNomediaChanged = { state.saveNomedia(it) },
            pureBlack = state.pureBlack,
            onPureBlackChanged = { state.savePureBlack(it) },
            mediaCacheSizeBytes = state.mediaCacheSizeBytes,
            favoriteCount = state.favoriteUris.size,
            isScanning = state.isScanning,
            onClearMediaCache = { state.clearMediaCacheOnly() },
            onClearFavorites = { state.clearFavorites() },
            onBack = { state.showAppSettings = false }
        )
    } else {
        FolderPickerScreen(
            folders = state.folders,
            onFoldersChanged = { state.saveFolders(it) },
            onRescan = { state.rescanMedia() },
            onStopScan = { state.stopScanning() },
            onStartBrowsing = { state.startBrowsing() },
            onStartFavorites = { state.startFavoriteBrowsing() },
            onOpenSettings = { state.showAppSettings = true },
            isScanning = state.isScanning,
            scanProgress = state.scanProgress,
            mediaCount = state.mediaItems.size,
            favoriteCount = state.favoriteUris.size,
            hasScanned = state.hasScanned,
            canResumeScan = !state.hasScanned && state.mediaItems.isNotEmpty()
        )
    }

    // ── Media load error dialog ──────────────────────────────
    if (state.mediaLoadError) {
        AlertDialog(
            onDismissRequest = { state.mediaLoadError = false },
            title = { Text("媒体读取失败") },
            text = { Text("文件可能已删除、移动或授权已失效。请重新扫描刷新媒体列表。") },
            confirmButton = {
                TextButton(onClick = {
                    state.mediaLoadError = false
                    state.isViewing = false
                    state.isFavoriteBrowsing = false
                    state.subfolderFilterUri = null
                    state.rescanMedia()
                }) { Text("重新扫描") }
            },
            dismissButton = {
                TextButton(onClick = { state.mediaLoadError = false }) { Text("稍后处理") }
            }
        )
    }

    // ── Scan message dialog ──────────────────────────────────
    if (state.scanMessage != null) {
        AlertDialog(
            onDismissRequest = { state.scanMessage = null },
            title = { Text("扫描提示") },
            text = { Text(state.scanMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { state.scanMessage = null }) { Text("知道了") }
            }
        )
    }

    // ── Favorite hint dialog ─────────────────────────────────
    val favoriteHint = state.favoriteHint
    if (favoriteHint != null) {
        AlertDialog(
            onDismissRequest = { state.favoriteHint = null },
            title = { Text("收藏提示") },
            text = { Text(favoriteHint.message) },
            confirmButton = {
                when (favoriteHint.action) {
                    FavoriteHintAction.StartBrowsing -> TextButton(onClick = {
                        state.favoriteHint = null
                        state.startBrowsing()
                    }) { Text("开始浏览") }
                    FavoriteHintAction.Rescan -> TextButton(onClick = {
                        state.favoriteHint = null
                        state.rescanMedia()
                    }) { Text("重新扫描") }
                }
            },
            dismissButton = {
                TextButton(onClick = { state.favoriteHint = null }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun ViewerSettingsDialog(state: MediaBrowserState) {
    AlertDialog(
        onDismissRequest = { state.showSettings = false },
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("遵守 .nomedia", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.respectNomedia,
                        onCheckedChange = { state.saveNomedia(it) }
                    )
                }

                Text("子文件夹模式排序")
                SubfolderSortMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.subfolderSortMode == mode,
                            onClick = { state.saveSubfolderSortMode(mode) }
                        )
                        Text(mode.label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("逆序排列", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.subfolderSortMode != SubfolderSortMode.Random && state.subfolderSortDescending,
                        onCheckedChange = { state.saveSubfolderSortDescending(it) },
                        enabled = state.subfolderSortMode != SubfolderSortMode.Random
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.showSettings = false }) { Text("关闭") }
        }
    )
}
