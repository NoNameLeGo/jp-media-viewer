package com.jp.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.app.BuildConfig
import com.jp.app.data.MediaScanner

private const val PROJECT_URL = "https://github.com/NoNameLeGo/jp-media-viewer"
private const val ISSUES_URL = "https://github.com/NoNameLeGo/jp-media-viewer/issues"
private const val DEVELOPER_NAME = "NoNameLeGo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    folders: List<String>,
    respectNomedia: Boolean,
    onFoldersChanged: (List<String>) -> Unit,
    onRespectNomediaChanged: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onStopScan: () -> Unit,
    onStartBrowsing: () -> Unit,
    onStartFavorites: () -> Unit,
    isScanning: Boolean,
    scanProgress: MediaScanner.ScanProgress?,

    mediaCount: Int,
    favoriteCount: Int,
    hasScanned: Boolean,
    mediaCacheSizeBytes: Long,
    onClearMediaCache: () -> Unit,
    onClearFavorites: () -> Unit
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var openLinkError by remember { mutableStateOf<String?>(null) }
    var folderPickError by remember { mutableStateOf<String?>(null) }
    var folderDetailUri by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (granted) {
            if (uri.toString() !in folders) {
                onFoldersChanged(folders + uri.toString())
            }
        } else {
            folderPickError = "无法获取文件夹授权，请重新选择目录。"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JP Media Viewer") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { folderPicker.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加文件夹")
            }

            Text("已添加 ${folders.size} 个文件夹")
            ScanStatus(
                isScanning = isScanning,
                scanProgress = scanProgress,

                mediaCount = mediaCount,
                hasScanned = hasScanned,
                hasFolders = folders.isNotEmpty()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStartBrowsing, enabled = mediaCount > 0) {
                    Text("开始浏览")
                }
                OutlinedButton(onClick = onStartFavorites, enabled = favoriteCount > 0) {
                    Text("查看收藏 ($favoriteCount)")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("遵守 .nomedia", modifier = Modifier.weight(1f))
                Switch(checked = respectNomedia, onCheckedChange = onRespectNomediaChanged)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRescan, enabled = folders.isNotEmpty() && !isScanning) {
                    Text("重新扫描")
                }
                OutlinedButton(onClick = onStopScan, enabled = isScanning) {
                    Text("停止扫描")
                }
            }
            CacheManagement(
                mediaCacheSizeBytes = mediaCacheSizeBytes,
                favoriteCount = favoriteCount,
                isScanning = isScanning,
                onClearMediaCache = onClearMediaCache,
                onClearFavorites = onClearFavorites
            )
            if (folders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { folder ->
                        val folderLabel = folderDisplayName(folder)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = folderLabel,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { folderDetailUri = folder },
                            trailingContent = {
                                IconButton(onClick = { onFoldersChanged(folders - folder) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenProject = {
                openLinkError = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                }.onFailure {
                    openLinkError = "无法打开项目地址，请检查是否有可用浏览器。"
                }
            },
            onOpenIssues = {
                openLinkError = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)))
                }.onFailure {
                    openLinkError = "无法打开问题反馈，请检查是否有可用浏览器。"
                }
            }
        )
    }

    if (openLinkError != null) {
        AlertDialog(
            onDismissRequest = { openLinkError = null },
            title = { Text("打开失败") },
            text = { Text(openLinkError ?: "") },
            confirmButton = {
                TextButton(onClick = { openLinkError = null }) {
                    Text("知道了")
                }
            }
        )
    }

    if (folderPickError != null) {
        AlertDialog(
            onDismissRequest = { folderPickError = null },
            title = { Text("授权失败") },
            text = { Text(folderPickError ?: "") },
            confirmButton = {
                TextButton(onClick = { folderPickError = null }) {
                    Text("知道了")
                }
            }
        )
    }

    if (folderDetailUri != null) {
        FolderDetailDialog(
            folderUri = folderDetailUri!!,
            onDismiss = { folderDetailUri = null }
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenIssues: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 JP Media Viewer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "本地随机图片/视频浏览器，支持文件夹授权、收藏、手势浏览和媒体缓存。",
                    style = MaterialTheme.typography.bodyMedium
                )
                AboutRow(label = "版本号", value = BuildConfig.VERSION_NAME)
                AboutRow(label = "构建类型", value = BuildConfig.BUILD_TYPE)
                AboutRow(label = "开发者", value = DEVELOPER_NAME)
                AboutRow(label = "许可证", value = "AGPL-3.0")
                AboutRow(label = "项目地址", value = PROJECT_URL)
                AboutRow(label = "问题反馈", value = ISSUES_URL)
                AboutRow(
                    label = "隐私说明",
                    value = "媒体扫描、收藏和缓存均保存在本机，不会上传你的文件。"
                )
                AboutRow(
                    label = "第三方组件",
                    value = "Jetpack Compose、Coil、Media3、AndroidX DocumentFile"
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onOpenProject) {
                    Text("项目地址")
                }
                TextButton(onClick = onOpenIssues) {
                    Text("问题反馈")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun folderDisplayName(folderUri: String): String {
    return runCatching { Uri.parse(folderUri).lastPathSegment }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: folderUri
}

@Composable
private fun FolderDetailDialog(
    folderUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val displayName = remember(folderUri) { folderDisplayName(folderUri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件夹详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("显示名称：$displayName")
                Text("完整 URI：$folderUri")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(folderUri))
                Toast.makeText(context, "已复制完整 URI", Toast.LENGTH_SHORT).show()
            }) {
                Text("复制 URI")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun CacheManagement(
    mediaCacheSizeBytes: Long,
    favoriteCount: Int,
    isScanning: Boolean,
    onClearMediaCache: () -> Unit,
    onClearFavorites: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "缓存管理",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "媒体缓存：${formatCacheSize(mediaCacheSizeBytes)} · 收藏：${favoriteCount} 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClearMediaCache, enabled = !isScanning && mediaCacheSizeBytes > 0L) {
                    Text("清除媒体缓存")
                }
                OutlinedButton(onClick = onClearFavorites, enabled = !isScanning && favoriteCount > 0) {
                    Text("清除收藏")
                }
            }
        }
    }
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = listOf("B", "KB", "MB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }

    return if (unitIndex == 0) {
        "$bytes ${units[unitIndex]}"
    } else {
        "%.1f %s".format(value, units[unitIndex])
    }
}

@Composable
private fun ScanStatus(
    isScanning: Boolean,
    scanProgress: MediaScanner.ScanProgress?,

    mediaCount: Int,
    hasScanned: Boolean,
    hasFolders: Boolean
) {
    if (!hasFolders) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }

            val text = when {
                isScanning -> {
                    val scanned = scanProgress?.scanned ?: 0
                    val found = scanProgress?.found ?: mediaCount
                    val currentFile = scanProgress?.currentFile?.takeIf { it.isNotBlank() }
                    val details = scanProgress?.scanDetailsText().orEmpty()
                    if (currentFile == null) {
                        "正在扫描：已检查 ${scanned} 个文件，找到 ${found} 个媒体（可先行浏览）$details"
                    } else {
                        "正在扫描：已检查 ${scanned} 个文件，找到 ${found} 个媒体（可先行浏览）$details\n${currentFile}"
                    }
                }
                hasScanned && mediaCount > 0 -> {
                    val scanned = scanProgress?.scanned ?: 0
                    val details = scanProgress?.scanDetailsText().orEmpty()
                    "扫描完成：已检查 ${scanned} 个文件，找到 ${mediaCount} 个媒体，可以直接开始浏览$details"
                }
                hasScanned -> {
                    val scanned = scanProgress?.scanned ?: 0
                    val details = scanProgress?.scanDetailsText().orEmpty()
                    "扫描完成：已检查 ${scanned} 个文件，找到 0 个媒体$details\n可能原因：.nomedia 过滤、目录无媒体、文件夹授权失效"
                }
                else -> "添加文件夹后会自动扫描"
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun MediaScanner.ScanProgress.scanDetailsText(): String {
    if (elapsedMillis == 0L && skippedNomediaDirs == 0 && failedDirs == 0 && folderStats.isEmpty()) return ""

    val summary = "耗时 ${formatDuration(elapsedMillis)}，跳过 .nomedia 目录 ${skippedNomediaDirs} 个，失败目录 ${failedDirs} 个"
    val folders = folderStats.joinToString(separator = "\n") { stats ->
        "- ${stats.name}：检查 ${stats.scanned} 个，媒体 ${stats.found} 个，跳过 ${stats.skippedNomediaDirs} 个，失败 ${stats.failedDirs} 个"
    }
    return if (folders.isBlank()) {
        "\n$summary"
    } else {
        "\n$summary\n$folders"
    }
}

private fun formatDuration(elapsedMillis: Long): String {
    if (elapsedMillis < 1000L) return "${elapsedMillis}ms"
    val seconds = elapsedMillis / 1000L
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return if (minutes == 0L) {
        "${seconds}s"
    } else {
        "${minutes}m ${remainingSeconds}s"
    }
}
