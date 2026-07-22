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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jp.app.data.MediaScanner
import com.jp.app.ui.theme.spaces

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    folders: List<String>,
    onFoldersChanged: (List<String>) -> Unit,
    onRescan: () -> Unit,
    onStopScan: () -> Unit,
    onStartBrowsing: () -> Unit,
    onStartFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    isScanning: Boolean,
    scanProgress: MediaScanner.ScanProgress?,
    mediaCount: Int,
    favoriteCount: Int,
    hasScanned: Boolean,
    canResumeScan: Boolean
) {
    val context = LocalContext.current
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(MaterialTheme.spaces.large)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.large)
        ) {
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(MaterialTheme.spaces.small))
                Text("添加文件夹")
            }

            ScanStatusComponent(
                isScanning = isScanning,
                scanProgress = scanProgress,
                mediaCount = mediaCount,
                hasScanned = hasScanned,
                hasFolders = folders.isNotEmpty()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.medium)) {
                Button(
                    onClick = onStartBrowsing,
                    enabled = mediaCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始浏览")
                }
                OutlinedButton(
                    onClick = onStartFavorites,
                    enabled = favoriteCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看收藏 ($favoriteCount)")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.medium)) {
                OutlinedButton(
                    onClick = onRescan,
                    enabled = folders.isNotEmpty() && !isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (canResumeScan) "继续补扫" else "重新扫描")
                }
                OutlinedButton(
                    onClick = onStopScan,
                    enabled = isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("暂停扫描")
                }
            }

            if (folders.isNotEmpty()) {
                Text(
                    text = "已添加 ${folders.size} 个文件夹",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spaces.small)) {
                    folders.forEach { folder ->
                        FolderCard(
                            label = folderDisplayName(folder),
                            onClick = { folderDetailUri = folder },
                            onDelete = { onFoldersChanged(folders - folder) }
                        )
                    }
                }
            }
        }
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
private fun FolderCard(
    label: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        },
        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
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
