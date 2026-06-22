package com.jp.app.ui

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun deepestFolderNameInternal(folderUri: Uri): String {
    val segment = folderUri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: return folderUri.toString()
    return segment.substringAfterLast('/').substringAfterLast(':').takeIf { it.isNotBlank() }
        ?: segment
}

fun deepestFolderName(folderUri: Uri): String {
    return deepestFolderNameInternal(folderUri)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "未知"

    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }

    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(size, units[unitIndex])
    }
}

private val detailDateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

fun formatModifiedDate(modifiedAt: Long): String {
    if (modifiedAt <= 0L) return "未知"
    return detailDateFormatter.format(Instant.ofEpochMilli(modifiedAt))
}
