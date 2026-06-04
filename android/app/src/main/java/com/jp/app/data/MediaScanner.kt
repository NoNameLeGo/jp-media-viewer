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

    data class FolderScanStats(
        val folderUri: String,
        val folderName: String,
        val scanned: Int,
        val found: Int
    )

    data class ScanSummary(
        val scanned: Int,
        val found: Int,
        val skippedNomedia: Int,
        val failedDirectories: List<String>,
        val elapsedMs: Long,
        val folders: List<FolderScanStats>
    )

    data class ScanResult(
        val items: List<MediaItem>,
        val summary: ScanSummary
    )

    data class ScanProgress(
        val scanned: Int,
        val found: Int,
        val totalFiles: Int? = null,
        val currentFile: String = "",
        val skippedNomedia: Int = 0,
        val failedDirectories: Int = 0
    )

    private data class FolderAccumulator(
        val folderUri: String,
        val folderName: String,
        var scanned: Int = 0,
        var found: Int = 0
    )

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val results = mutableListOf<MediaItem>()
        val folders = mutableListOf<FolderAccumulator>()
        val failedDirectories = mutableListOf<String>()
        var scanned = 0
        var skippedNomedia = 0

        suspend fun publishProgress() {
            onProgress(
                ScanProgress(
                    scanned = scanned,
                    found = results.size,
                    skippedNomedia = skippedNomedia,
                    failedDirectories = failedDirectories.size
                )
            )
        }

        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null) {
                failedDirectories.add(uriString)
                publishProgress()
                continue
            }

            val folderStats = FolderAccumulator(
                folderUri = uriString,
                folderName = rootDoc.safeName() ?: treeUri.lastPathSegment ?: uriString
            )
            folders.add(folderStats)

            val scanState = scanDirectory(
                dir = rootDoc,
                rootUri = treeUri,
                respectNomedia = respectNomedia,
                results = results,
                scanned = scanned,
                skippedNomedia = skippedNomedia,
                failedDirectories = failedDirectories,
                folderStats = folderStats,
                onProgress = { count, skipped ->
                    scanned = count
                    skippedNomedia = skipped
                    publishProgress()
                }
            )
            scanned = scanState.scanned
            skippedNomedia = scanState.skippedNomedia
        }

        val summary = ScanSummary(
            scanned = scanned,
            found = results.size,
            skippedNomedia = skippedNomedia,
            failedDirectories = failedDirectories.toList(),
            elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
            folders = folders.map { folder ->
                FolderScanStats(
                    folderUri = folder.folderUri,
                    folderName = folder.folderName,
                    scanned = folder.scanned,
                    found = folder.found
                )
            }
        )
        onProgress(
            ScanProgress(
                scanned = summary.scanned,
                found = summary.found,
                skippedNomedia = summary.skippedNomedia,
                failedDirectories = summary.failedDirectories.size
            )
        )
        ScanResult(items = results.toList(), summary = summary)
    }

    private data class ScanState(
        val scanned: Int,
        val skippedNomedia: Int
    )

    private suspend fun scanDirectory(
        dir: DocumentFile,
        rootUri: Uri,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        scanned: Int,
        skippedNomedia: Int,
        failedDirectories: MutableList<String>,
        folderStats: FolderAccumulator,
        onProgress: suspend (Int, Int) -> Unit
    ): ScanState {
        if (respectNomedia && runCatching { dir.findFile(".nomedia") != null }.getOrDefault(false)) {
            return ScanState(scanned = scanned, skippedNomedia = skippedNomedia + 1)
        }

        val files = runCatching { dir.listFiles() }.getOrElse {
            failedDirectories.add(dir.safeName() ?: dir.uri.toString())
            return ScanState(scanned = scanned, skippedNomedia = skippedNomedia)
        }

        var scannedCount = scanned
        var skippedCount = skippedNomedia
        for (file in files) {
            if (file.safeIsDirectory()) {
                val state = scanDirectory(
                    dir = file,
                    rootUri = rootUri,
                    respectNomedia = respectNomedia,
                    results = results,
                    scanned = scannedCount,
                    skippedNomedia = skippedCount,
                    failedDirectories = failedDirectories,
                    folderStats = folderStats,
                    onProgress = onProgress
                )
                scannedCount = state.scanned
                skippedCount = state.skippedNomedia
            } else if (file.safeIsFile()) {
                scannedCount++
                folderStats.scanned++
                val fileName = file.safeName()
                val mime = file.safeType()?.takeIf { it.isNotBlank() } ?: inferMimeType(fileName)
                if (isSupportedMedia(mime)) {
                    folderStats.found++
                    results.add(
                        MediaItem(
                            uri = file.uri,
                            name = fileName ?: "unknown",
                            mimeType = mime,
                            size = file.safeLength(),
                            folderUri = rootUri,
                            modifiedAt = file.safeLastModified()
                        )
                    )
                }
                if (scannedCount % PROGRESS_UPDATE_INTERVAL == 0) {
                    onProgress(scannedCount, skippedCount)
                }
            }
        }
        return ScanState(scanned = scannedCount, skippedNomedia = skippedCount)
    }

    private fun DocumentFile.safeName(): String? {
        return runCatching { name }.getOrNull()
    }

    private fun DocumentFile.safeType(): String? {
        return runCatching { type }.getOrNull()
    }

    private fun DocumentFile.safeLength(): Long {
        return runCatching { length() }.getOrDefault(0L)
    }

    private fun DocumentFile.safeLastModified(): Long {
        return runCatching { lastModified() }.getOrDefault(0L)
    }

    private fun DocumentFile.safeIsDirectory(): Boolean {
        return runCatching { isDirectory }.getOrDefault(false)
    }

    private fun DocumentFile.safeIsFile(): Boolean {
        return runCatching { isFile }.getOrDefault(false)
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
