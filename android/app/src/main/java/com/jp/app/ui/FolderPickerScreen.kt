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
    canResumeScan: Boolean,
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
            ScanStatusComponent(
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
                    Text(if (canResumeScan) "继续补扫" else "重新扫描")
                }
                OutlinedButton(onClick = onStopScan, enabled = isScanning) {
                    Text("暂停扫描")
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
                    value = "Jetpack Compose、Coil、Media3"
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
