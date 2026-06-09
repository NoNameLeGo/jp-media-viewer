package com.jp.app.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaScanner(private val context: Context) {

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL = 20
        const val ITEMS_PROGRESS_UPDATE_INTERVAL = 500
    }

    data class ScanProgress(
        val scanned: Int,
        val found: Int,
        val foundItems: List<MediaItem> = emptyList(),
        val totalFiles: Int? = null,
        val currentFile: String = "",
        val skippedNomediaDirs: Int = 0,
        val failedDirs: Int = 0,
        val elapsedMillis: Long = 0L,
        val folderStats: List<FolderScanStats> = emptyList()
    )

    data class FolderScanStats(
        val name: String,
        val scanned: Int,
        val found: Int,
        val skippedNomediaDirs: Int,
        val failedDirs: Int
    )

    private data class ScanCounters(
        var scanned: Int,
        var found: Int = 0,
        var skippedNomediaDirs: Int = 0,
        var failedDirs: Int = 0
    )

    private data class MutableFolderScanStats(
        val name: String,
        var scanned: Int = 0,
        var found: Int = 0,
        var skippedNomediaDirs: Int = 0,
        var failedDirs: Int = 0
    ) {
        fun toFolderScanStats(): FolderScanStats {
            return FolderScanStats(
                name = name,
                scanned = scanned,
                found = found,
                skippedNomediaDirs = skippedNomediaDirs,
                failedDirs = failedDirs
            )
        }
    }

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()
        val knownUris = mutableSetOf<String>()
        val counters = ScanCounters(scanned = 0)
        val startedAt = SystemClock.elapsedRealtime()
        val folderStats = mutableListOf<MutableFolderScanStats>()

        suspend fun emitProgress(currentFile: String, includeItems: Boolean) {
            val foundItems = if (includeItems) results.toList() else emptyList()
            onProgress(
                ScanProgress(
                    scanned = counters.scanned,
                    found = counters.found,
                    foundItems = foundItems,
                    currentFile = currentFile,
                    skippedNomediaDirs = counters.skippedNomediaDirs,
                    failedDirs = counters.failedDirs,
                    elapsedMillis = SystemClock.elapsedRealtime() - startedAt,
                    folderStats = folderStats.map { it.toFolderScanStats() }
                )
            )
        }

        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            val stats = MutableFolderScanStats(rootDoc?.name ?: treeUri.lastPathSegment ?: "未知目录")
            folderStats.add(stats)
            if (rootDoc == null) {
                counters.failedDirs++
                stats.failedDirs++
                continue
            }
            scanDirectory(rootDoc, respectNomedia, results, knownUris, counters, stats, ::emitProgress)
        }

        emitProgress(currentFile = "", includeItems = true)
        results.toList()
    }

    private suspend fun scanDirectory(
        dir: DocumentFile,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        knownUris: MutableSet<String>,
        counters: ScanCounters,
        folderStats: MutableFolderScanStats,
        onProgress: suspend (String, Boolean) -> Unit
    ): Int {
        currentCoroutineContext().ensureActive()
        val hasNomedia = if (respectNomedia) {
            try {
                dir.findFile(".nomedia") != null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                counters.failedDirs++
                folderStats.failedDirs++
                return counters.scanned
            }
        } else {
            false
        }
        if (respectNomedia && hasNomedia) {
            counters.skippedNomediaDirs++
            folderStats.skippedNomediaDirs++
            return counters.scanned
        }

        onProgress("正在读取目录：${dir.name ?: dir.uri.lastPathSegment ?: "未知目录"}", false)
        val files = try {
            dir.listFiles()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            counters.failedDirs++
            folderStats.failedDirs++
            return counters.scanned
        }
        for (file in files) {
            currentCoroutineContext().ensureActive()
            if (file.isDirectory) {
                scanDirectory(file, respectNomedia, results, knownUris, counters, folderStats, onProgress)
            } else if (file.isFile) {
                counters.scanned++
                folderStats.scanned++
                val currentFile = file.name ?: file.uri.lastPathSegment ?: "未知文件"
                val mime = file.type?.takeIf { it.isNotBlank() } ?: inferMimeType(file.name)
                if (isSupportedMedia(mime)) {
                    val uriString = file.uri.toString()
                    if (knownUris.add(uriString)) {
                        counters.found++
                        folderStats.found++
                        results.add(
                            MediaItem(
                                uri = file.uri,
                                name = file.name ?: "unknown",
                                mimeType = mime,
                                size = file.length(),
                                folderUri = dir.uri,
                                modifiedAt = file.lastModified()
                            )
                        )
                    }
                }
                if (counters.scanned % PROGRESS_UPDATE_INTERVAL == 0) {
                    onProgress(currentFile, counters.scanned % ITEMS_PROGRESS_UPDATE_INTERVAL == 0)
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
