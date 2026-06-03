package com.jp.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class MediaScanner(private val context: Context) {

    data class ScanProgress(
        val scanned: Int,
        val found: Int,
        val totalFiles: Int? = null,
        val currentFile: String = ""
    )

    suspend fun scan(
        folderUris: List<String>,
        respectNomedia: Boolean,
        onProgress: (ScanProgress) -> Unit = {}
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = Collections.synchronizedList(mutableListOf<MediaItem>())
        var scanned = 0

        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            scanDirectory(rootDoc, treeUri, respectNomedia, results, scanned) { file ->
                scanned = file
                onProgress(ScanProgress(scanned = scanned, found = results.size, currentFile = ""))
            }
        }

        onProgress(ScanProgress(scanned = scanned, found = results.size))
        results.toList()
    }

    private fun scanDirectory(
        dir: DocumentFile,
        rootUri: Uri,
        respectNomedia: Boolean,
        results: MutableList<MediaItem>,
        scanned: Int,
        onProgress: (Int) -> Unit
    ) {
        if (respectNomedia && dir.findFile(".nomedia") != null) return

        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, rootUri, respectNomedia, results, scanned, onProgress)
            } else if (file.isFile) {
                val mime = file.type ?: ""
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    results.add(
                        MediaItem(
                            uri = file.uri,
                            name = file.name ?: "unknown",
                            mimeType = mime,
                            size = file.length(),
                            folderUri = rootUri
                        )
                    )
                }
            }
        }
    }
}
