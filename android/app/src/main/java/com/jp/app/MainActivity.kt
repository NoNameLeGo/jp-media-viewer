package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
            val colorScheme = if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(this)
            } else {
                dynamicLightColorScheme(this)
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

    fun saveFolders(newFolders: List<String>) {
        folders = newFolders
        prefs.edit().putStringSet("folder_uris", newFolders.toSet()).apply()
    }

    fun saveNomedia(value: Boolean) {
        respectNomedia = value
        prefs.edit().putBoolean("respect_nomedia", value).apply()
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
                        scope.launch {
                            isScanning = true
                            showSettings = false
                            val items = scanner.scan(folders, respectNomedia)
                            mediaItems = items.shuffled()
                            currentIndex = 0
                            isScanning = false
                        }
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
            onStartBrowsing = {
                scope.launch {
                    isScanning = true
                    val items = scanner.scan(folders, respectNomedia)
                    mediaItems = items.shuffled()
                    currentIndex = 0
                    isScanning = false
                    if (mediaItems.isNotEmpty()) {
                        isViewing = true
                    }
                }
            },
            isScanning = isScanning
        )
    }
}
