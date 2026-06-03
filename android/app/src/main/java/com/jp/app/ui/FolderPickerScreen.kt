package com.jp.app.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    folders: List<String>,
    respectNomedia: Boolean,
    onFoldersChanged: (List<String>) -> Unit,
    onRespectNomediaChanged: (Boolean) -> Unit,
    onStartBrowsing: () -> Unit,
    isScanning: Boolean
) {
    val context = LocalContext.current

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

            Button(
                onClick = onStartBrowsing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = folders.isNotEmpty() && !isScanning
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isScanning) "扫描中..." else "开始浏览")
            }
        }
    }
}
