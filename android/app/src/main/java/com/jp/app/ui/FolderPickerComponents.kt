package com.jp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jp.app.data.MediaScanner

@Composable
fun ScanStatusComponent(
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
                mediaCount > 0 -> {
                    val scanned = scanProgress?.scanned ?: 0
                    val details = scanProgress?.scanDetailsText().orEmpty()
                    "扫描已暂停：已检查 ${scanned} 个文件，找到 ${mediaCount} 个媒体，进度已保存${details}"
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
