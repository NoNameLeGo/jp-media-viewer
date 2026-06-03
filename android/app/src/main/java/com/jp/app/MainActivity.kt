package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
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
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val scanner = remember { MediaScanner(context) }

    var folders by remember {
        mutableStateOf(
            prefs.getStringSet("folder_uris", emptySet())?.toList() ?: emptyList()
        )
    }
    var respectNomedia by remember { mutableStateOf(prefs.getBoolean("respect_nomedia", true)) }
    var isScanning by remember { mutableStateOf(false) }
    var isViewing by remember { mutableStateOf(false) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    fun saveFolders(newFolders: List<String>) {
        folders = newFolders
        prefs.edit().putStringSet("folder_uris", newFolders.toSet()).apply()
    }

    fun saveNomedia(value: Boolean) {
        respectNomedia = value
        prefs.edit().putBoolean("respect_nomedia", value).apply()
    }

    fun scanMedia() {
        scope.launch {
            isScanning = true
            scanMessage = null
            try {
                val items = scanner.scan(folders, respectNomedia)
                mediaItems = items.shuffled()
                currentIndex = 0
                isViewing = mediaItems.isNotEmpty()
                if (mediaItems.isEmpty()) {
                    scanMessage = "没有找到图片或视频。请确认所选文件夹包含媒体文件，或尝试关闭 .nomedia 过滤后重新扫描。"
                }
            } catch (_: SecurityException) {
                isViewing = false
                scanMessage = "无法读取所选文件夹。请删除该文件夹后重新添加授权。"
            } catch (error: Exception) {
                isViewing = false
                scanMessage = "扫描失败：${error.localizedMessage ?: "未知错误"}"
            } finally {
                isScanning = false
            }
        }
    }

    if (isViewing && mediaItems.isNotEmpty()) {
        MediaViewerScreen(
            mediaItems = mediaItems,
            currentIndex = currentIndex,
            onNext = {
                if (mediaItems.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % mediaItems.size
                }
            },
            onBack = {
                isViewing = false
            },
            onSettings = { showSettings = !showSettings }
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
                        scanMedia()
                    }) {
                        androidx.compose.material3.Text("重新扫描")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showSettings = false }) {
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
            onStartBrowsing = { scanMedia() },
            isScanning = isScanning
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
