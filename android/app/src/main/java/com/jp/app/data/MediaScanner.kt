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

    private data class ScanCounters(
        var scanned: Int,
        var skipped: Int
    )

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        initialItems: List<MediaItem> = emptyList(),
        initialScanned: Int = 0,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = initialItems.toMutableList()
        val knownUris = initialItems.mapTo(mutableSetOf()) { it.uri.toString() }
        val counters = ScanCounters(scanned = initialScanned, skipped = 0)

        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            scanDirectory(rootDoc, respectNomedia, results, knownUris, initialScanned, counters) {
                onProgress(ScanProgress(scanned = counters.scanned, found = results.size, foundItems = results.toList(), currentFile = ""))
            }
        }

        onProgress(ScanProgress(scanned = counters.scanned, found = results.size, foundItems = results.toList()))
        results.toList()
    }

    private suspend fun scanDirectory(
        dir: DocumentFile,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        knownUris: MutableSet<String>,
        initialScanned: Int,
        counters: ScanCounters,
        onProgress: suspend () -> Unit
    ): Int {
        if (respectNomedia && dir.findFile(".nomedia") != null) return counters.scanned

        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, respectNomedia, results, knownUris, initialScanned, counters, onProgress)
            } else if (file.isFile) {
                if (counters.skipped < initialScanned) {
                    counters.skipped++
                    continue
                }
                counters.scanned++
                val mime = file.type?.takeIf { it.isNotBlank() } ?: inferMimeType(file.name)
                if (isSupportedMedia(mime)) {
                    val uriString = file.uri.toString()
                    if (knownUris.add(uriString)) {
                        results.add(
                            MediaItem(
                                uri = file.uri,
                                name = file.name ?: "unknown",
                                mimeType = mime,
                                size = file.length(),
                                folderUri = dir.uri,
                                modifiedAt = 0L
                            )
                        )
                    }
                }
                if (counters.scanned % PROGRESS_UPDATE_INTERVAL == 0) {
                    onProgress()
                }
            }
        }
        return counters.scanned
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
