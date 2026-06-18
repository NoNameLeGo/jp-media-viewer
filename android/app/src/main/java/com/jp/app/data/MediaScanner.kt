package com.jp.app.data

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

class MediaScanner(private val context: Context) {

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL = 100
        const val ITEMS_PROGRESS_UPDATE_INTERVAL = 2_000
        const val PROGRESS_UPDATE_MIN_INTERVAL_MS = 500L

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        const val COL_DOC_ID = 0
        const val COL_DISPLAY_NAME = 1
        const val COL_MIME_TYPE = 2
        const val COL_SIZE = 3
        const val COL_LAST_MODIFIED = 4

        const val MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR
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

    /**
     * Represents a child entry from a SAF directory query.
     */
    private data class ChildEntry(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        initialItems: List<MediaItem> = emptyList(),
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = initialItems.toMutableList()
        val knownUris = initialItems.asSequence().map { it.uriString }.toMutableSet()
        val counters = ScanCounters(scanned = 0, found = results.size)
        val startedAt = SystemClock.elapsedRealtime()
        val folderStats = mutableListOf<MutableFolderScanStats>()
        var lastProgressAt = 0L

        suspend fun emitProgress(currentFile: String, includeItems: Boolean, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && !includeItems && now - lastProgressAt < PROGRESS_UPDATE_MIN_INTERVAL_MS) return
            lastProgressAt = now
            val foundItems = if (includeItems) results.toList() else emptyList()
            onProgress(
                ScanProgress(
                    scanned = counters.scanned,
                    found = counters.found,
                    foundItems = foundItems,
                    currentFile = currentFile,
                    skippedNomediaDirs = counters.skippedNomediaDirs,
                    failedDirs = counters.failedDirs,
                    elapsedMillis = now - startedAt,
                    folderStats = folderStats.map { it.toFolderScanStats() }
                )
            )
        }
        try {
            for (uriString in folderUris) {
                val treeUri = Uri.parse(uriString)
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootName = rootDocId.substringAfterLast('/', rootDocId.substringAfterLast(':'))
                val stats = MutableFolderScanStats(rootName)
                folderStats.add(stats)

                scanDirectory(treeUri, rootDocId, respectNomedia, results, knownUris, counters, stats) { currentFile, includeItems ->
                    emitProgress(currentFile, includeItems)
                }
            }

            emitProgress(currentFile = "", includeItems = true, force = true)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                emitProgress(currentFile = "", includeItems = true, force = true)
            }
            throw error
        }
        results.toList()
    }

    /**
     * Query children of a directory using direct ContentResolver cursor.
     * Much faster than DocumentFile.listFiles() which creates a DocumentFile per entry.
     */
    private fun queryChildren(treeUri: Uri, parentDocId: String): List<ChildEntry>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val entries = mutableListOf<ChildEntry>()
        try {
            context.contentResolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(COL_DOC_ID) ?: continue
                    val name = cursor.getString(COL_DISPLAY_NAME) ?: ""
                    val mime = cursor.getString(COL_MIME_TYPE) ?: ""
                    val size = cursor.getLong(COL_SIZE)
                    val modified = cursor.getLong(COL_LAST_MODIFIED)
                    entries.add(
                        ChildEntry(
                            documentId = docId,
                            displayName = name,
                            mimeType = mime,
                            size = size,
                            lastModified = modified,
                            isDirectory = mime == MIME_DIR
                        )
                    )
                }
            } ?: return null
        } catch (_: Exception) {
            return null
        }
        return entries
    }

    private suspend fun scanDirectory(
        treeUri: Uri,
        dirDocId: String,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        knownUris: MutableSet<String>,
        counters: ScanCounters,
        folderStats: MutableFolderScanStats,
        onProgress: suspend (String, Boolean) -> Unit
    ): Int {
        currentCoroutineContext().ensureActive()

        val dirName = dirDocId.substringAfterLast('/', dirDocId.substringAfterLast(':'))
        onProgress("正在读取目录：$dirName", false)

        val children = queryChildren(treeUri, dirDocId)
        if (children == null) {
            counters.failedDirs++
            folderStats.failedDirs++
            return counters.scanned
        }

        // Check .nomedia in query results (no extra round-trip)
        if (respectNomedia && children.any { !it.isDirectory && it.displayName == ".nomedia" }) {
            counters.skippedNomediaDirs++
            folderStats.skippedNomediaDirs++
            return counters.scanned
        }

        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, dirDocId)

        for (child in children) {
            currentCoroutineContext().ensureActive()

            if (child.isDirectory) {
                scanDirectory(treeUri, child.documentId, respectNomedia, results, knownUris, counters, folderStats, onProgress)
            } else {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                val uriString = childUri.toString()
                if (uriString in knownUris) continue

                counters.scanned++
                folderStats.scanned++

                val mime = child.mimeType.takeIf { it.isNotBlank() } ?: inferMimeType(child.displayName)
                if (isSupportedMedia(mime) && knownUris.add(uriString)) {
                    counters.found++
                    folderStats.found++
                    results.add(
                        MediaItem(
                            uri = childUri,
                            name = child.displayName.ifBlank { "unknown" },
                            mimeType = mime,
                            size = child.size,
                            folderUri = dirUri,
                            modifiedAt = child.lastModified
                        )
                    )
                }
                if (counters.scanned % PROGRESS_UPDATE_INTERVAL == 0) {
                    onProgress(child.displayName, counters.scanned % ITEMS_PROGRESS_UPDATE_INTERVAL == 0)
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
