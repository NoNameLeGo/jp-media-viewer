package com.jp.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
private const val DEVELOPER_NAME = "NoNameLeGo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    folders: List<String>,
    respectNomedia: Boolean,
    onFoldersChanged: (List<String>) -> Unit,
    onRespectNomediaChanged: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onStartBrowsing: () -> Unit,
    onStartFavorites: () -> Unit,
    isScanning: Boolean,
    scanProgress: MediaScanner.ScanProgress?,
    mediaCount: Int,
    favoriteCount: Int,
    hasScanned: Boolean
) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            if (uri.toString() !in folders) {
                onFoldersChanged(folders + uri.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随机浏览") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("选择文件夹", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请添加一个或多个文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders, key = { it }) { uriString ->
                        val displayName = try {
                            Uri.parse(uriString).lastPathSegment ?: uriString
                        } catch (_: Exception) {
                            uriString
                        }
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Default.Folder, null)
                            },
                            headlineContent = {
                                Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(uriString, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    try {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(uriString),
                                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (_: SecurityException) {
                                        // Permission may already be gone if Android revoked it.
                                    }
                                    onFoldersChanged(folders - uriString)
                                }) {
                                    Icon(Icons.Default.Delete, "移除")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("添加文件夹")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("遵守 .nomedia 文件", modifier = Modifier.weight(1f))
                Switch(
                    checked = respectNomedia,
                    onCheckedChange = onRespectNomediaChanged
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ScanStatus(
                isScanning = isScanning,
                scanProgress = scanProgress,
                mediaCount = mediaCount,
                hasScanned = hasScanned,
                hasFolders = folders.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = folders.isNotEmpty() && !isScanning
            ) {
                Text("重新扫描")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onStartBrowsing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = folders.isNotEmpty() && !isScanning && mediaCount > 0
            ) {
                Text("开始浏览")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onStartFavorites,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isScanning
            ) {
                Text("查看收藏（$favoriteCount）")
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenProject = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
            }
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenProject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 JP Media Viewer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow(label = "版本号", value = BuildConfig.VERSION_NAME)
                AboutRow(label = "开发者", value = DEVELOPER_NAME)
                AboutRow(label = "项目地址", value = PROJECT_URL)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenProject) {
                Text("打开项目地址")
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
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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
                    "正在扫描：已检查 ${scanned} 个文件，找到 ${found} 个媒体"
                }
                hasScanned && mediaCount > 0 -> {
                    val scanned = scanProgress?.scanned ?: 0
                    "扫描完成：已检查 ${scanned} 个文件，找到 ${mediaCount} 个媒体，可以直接开始浏览"
                }
                hasScanned -> {
                    val scanned = scanProgress?.scanned ?: 0
                    "扫描完成：已检查 ${scanned} 个文件，找到 0 个媒体\n可能原因：.nomedia 过滤、目录无媒体、文件夹授权失效"
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
