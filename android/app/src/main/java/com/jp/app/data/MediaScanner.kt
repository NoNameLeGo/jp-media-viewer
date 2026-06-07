package com.jp.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaScanner(private val context: Context) {

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL = 20
    }

    data class ScanProgress(
        val scanned: Int,
        val found: Int,
        val foundItems: List<MediaItem> = emptyList(),
        val totalFiles: Int? = null,
        val currentFile: String = ""
    )

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()
        var scanned = 0

        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            scanned = scanDirectory(rootDoc, treeUri, respectNomedia, results, scanned) { count ->
                scanned = count
                onProgress(ScanProgress(scanned = scanned, found = results.size, foundItems = results.toList(), currentFile = ""))
            }
        }

        onProgress(ScanProgress(scanned = scanned, found = results.size, foundItems = results.toList()))
        results.toList()
    }

    private suspend fun scanDirectory(
        dir: DocumentFile,
        rootUri: Uri,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        scanned: Int,
        onProgress: suspend (Int) -> Unit
    ): Int {
        if (respectNomedia && dir.findFile(".nomedia") != null) return scanned

        val files = dir.listFiles()
        var scannedCount = scanned
        for (file in files) {
            if (file.isDirectory) {
                scannedCount = scanDirectory(file, rootUri, respectNomedia, results, scannedCount, onProgress)
            } else if (file.isFile) {
                scannedCount++
                val mime = file.type?.takeIf { it.isNotBlank() } ?: inferMimeType(file.name)
                if (isSupportedMedia(mime)) {
                    results.add(
                        MediaItem(
                            uri = file.uri,
                            name = file.name ?: "unknown",
                            mimeType = mime,
                            size = file.length(),
                            folderUri = rootUri,
                            modifiedAt = 0L
                        )
                    )
                }
                if (scannedCount % PROGRESS_UPDATE_INTERVAL == 0) {
                    onProgress(scannedCount)
                }
            }
        }
        return scannedCount
    }

    private fun isSupportedMedia(mimeType: String): Boolean {
        return mimeType.startsWith("image/") || mimeType.startsWith("video/")
    }

    private fun inferMimeType(fileName: String?): String {
        return when (fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> ""
        }
    }
}
