package com.jp.app.ui

import android.net.Uri

fun folderDisplayName(folderUri: String): String {
    return runCatching { Uri.parse(folderUri).lastPathSegment }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: folderUri
}
