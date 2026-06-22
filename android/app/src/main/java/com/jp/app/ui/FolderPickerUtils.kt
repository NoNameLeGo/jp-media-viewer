package com.jp.app.ui

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

fun formatModifiedDate(modifiedAt: Long): String {
    if (modifiedAt <= 0L) return "未知"
    return detailDateFormatter.format(Instant.ofEpochMilli(modifiedAt))
}

fun folderDisplayName(folderUri: String): String {
    return runCatching { Uri.parse(folderUri).lastPathSegment }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: folderUri
}
