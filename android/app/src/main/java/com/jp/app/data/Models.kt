package com.jp.app.data

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val folderUri: Uri,
    val modifiedAt: Long
) {
    /** Cached URI string to avoid repeated allocation in hot paths (favorites checks, filters). */
    val uriString: String = uri.toString()

    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class AppSettings(
    val folderUris: List<String> = emptyList(),
    val respectNomedia: Boolean = true
)
