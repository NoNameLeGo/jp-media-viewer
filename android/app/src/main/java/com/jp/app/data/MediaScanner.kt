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
        const val MAX_REPORTED_FAILED_DIRECTORIES = 50
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

    private data class DirectoryFrame(
        val dir: DocumentFile,
        val rootUri: Uri,
        val folderStats: FolderAccumulator
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
        var failedDirectoryCount = 0

        fun addFailedDirectory(name: String) {
            failedDirectoryCount++
            if (failedDirectories.size < MAX_REPORTED_FAILED_DIRECTORIES) {
                failedDirectories.add(name)
            }
        }

        suspend fun publishProgress() {
            onProgress(
                ScanProgress(
                    scanned = scanned,
                    found = results.size,
                    skippedNomedia = skippedNomedia,
                    failedDirectories = failedDirectoryCount
                )
            )
        }

        for (uriString in folderUris) {
            val treeUri = runCatching { Uri.parse(uriString) }.getOrNull()
            if (treeUri == null) {
                addFailedDirectory(uriString)
                publishProgress()
                continue
            }

            val rootDoc = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            if (rootDoc == null) {
                addFailedDirectory(uriString)
                publishProgress()
                continue
            }

            val folderStats = FolderAccumulator(
                folderUri = uriString,
                folderName = rootDoc.safeName() ?: treeUri.lastPathSegment ?: uriString
            )
            folders.add(folderStats)

            val stack = ArrayDeque<DirectoryFrame>()
            stack.addLast(DirectoryFrame(rootDoc, treeUri, folderStats))

            while (stack.isNotEmpty()) {
                val frame = stack.removeLast()
                val dir = frame.dir

                if (respectNomedia && dir.safeHasNomedia()) {
                    skippedNomedia++
                    continue
                }

                val files = dir.safeListFiles()
                if (files == null) {
                    addFailedDirectory(dir.safeName() ?: dir.safeUriString())
                    publishProgress()
                    continue
                }

                for (file in files) {
                    if (file.safeIsDirectory()) {
                        stack.addLast(DirectoryFrame(file, frame.rootUri, frame.folderStats))
                    } else if (file.safeIsFile()) {
                        scanned++
                        frame.folderStats.scanned++
                        val fileName = file.safeName()
                        val mime = file.safeType()?.takeIf { it.isNotBlank() } ?: inferMimeType(fileName)
                        if (isSupportedMedia(mime)) {
                            val fileUri = file.safeUri() ?: continue
                            frame.folderStats.found++
                            results.add(
                                MediaItem(
                                    uri = fileUri,
                                    name = fileName ?: "unknown",
                                    mimeType = mime,
                                    size = file.safeLength(),
                                    folderUri = frame.rootUri,
                                    modifiedAt = file.safeLastModified()
                                )
                            )
                        }
                        if (scanned % PROGRESS_UPDATE_INTERVAL == 0) {
                            publishProgress()
                        }
                    }
                }
            }
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
                failedDirectories = failedDirectoryCount
            )
        )
        ScanResult(items = results.toList(), summary = summary)
    }

    private fun DocumentFile.safeHasNomedia(): Boolean {
        return runCatching { findFile(".nomedia") != null }.getOrDefault(false)
    }

    private fun DocumentFile.safeListFiles(): Array<DocumentFile>? {
        return runCatching { listFiles() }.getOrNull()
    }

    private fun DocumentFile.safeName(): String? {
        return runCatching { name }.getOrNull()
    }

    private fun DocumentFile.safeType(): String? {
        return runCatching { type }.getOrNull()
    }

    private fun DocumentFile.safeUri(): Uri? {
        return runCatching { uri }.getOrNull()
    }

    private fun DocumentFile.safeUriString(): String {
        return safeUri()?.toString() ?: "未知目录"
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
