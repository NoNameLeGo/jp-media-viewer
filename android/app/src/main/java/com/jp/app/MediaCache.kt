package com.jp.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import com.jp.app.data.MediaItem
import java.io.File
import java.io.StringReader

private const val PREF_MEDIA_CACHE_FOLDERS = "media_cache_folders"
private const val PREF_MEDIA_CACHE_RESPECT_NOMEDIA = "media_cache_respect_nomedia"
private const val PREF_MEDIA_CACHE_SCANNED = "media_cache_scanned"
private const val PREF_MEDIA_CACHE_COMPLETE = "media_cache_complete"
private const val PREF_MEDIA_CACHE_ITEMS = "media_cache_items"
const val MEDIA_CACHE_FILE_NAME = "media_scan_cache.json"
const val PARTIAL_CACHE_MIN_ITEM_DELTA = 1_000
const val PARTIAL_CACHE_MIN_INTERVAL_MS = 10_000L

public data class CachedMediaScan(
    val items: List<MediaItem>,
    val scanned: Int,
    val complete: Boolean
)

fun mediaCacheFile(context: Context): File {
    return File(context.filesDir, MEDIA_CACHE_FILE_NAME)
}

fun mediaCacheFoldersKey(folders: List<String>): String {
    return folders.sorted().joinToString("\n")
}

fun loadCachedMediaScan(
    context: Context,
    prefs: SharedPreferences,
    folders: List<String>,
    respectNomedia: Boolean
): CachedMediaScan? {
    if (prefs.getString(PREF_MEDIA_CACHE_FOLDERS, null) != mediaCacheFoldersKey(folders)) return null
    if (prefs.getBoolean(PREF_MEDIA_CACHE_RESPECT_NOMEDIA, !respectNomedia) != respectNomedia) return null

    val cachedScan = runCatching {
        val cacheFile = mediaCacheFile(context)
        val items = if (cacheFile.exists()) {
            cacheFile.bufferedReader().use { reader -> readMediaItems(JsonReader(reader)) }
        } else {
            val legacyCacheJson = prefs.getString(PREF_MEDIA_CACHE_ITEMS, null) ?: return null
            StringReader(legacyCacheJson).use { reader -> readMediaItems(JsonReader(reader)) }
        }
        CachedMediaScan(
            items = items,
            scanned = prefs.getInt(PREF_MEDIA_CACHE_SCANNED, items.size),
            complete = prefs.getBoolean(PREF_MEDIA_CACHE_COMPLETE, true)
        )
    }.getOrNull()

    return cachedScan
}

fun saveCachedMediaScan(
    context: Context,
    prefs: SharedPreferences,
    folders: List<String>,
    respectNomedia: Boolean,
    items: List<MediaItem>,
    scanned: Int,
    complete: Boolean
) {
    val cacheFile = mediaCacheFile(context)
    val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
    tempFile.parentFile?.mkdirs()
    tempFile.bufferedWriter().use { writer ->
        JsonWriter(writer).use { jsonWriter -> writeMediaItems(jsonWriter, items) }
    }
    if (!tempFile.renameTo(cacheFile)) {
        cacheFile.delete()
        check(tempFile.renameTo(cacheFile)) { "无法写入媒体缓存" }
    }

    prefs.edit()
        .putString(PREF_MEDIA_CACHE_FOLDERS, mediaCacheFoldersKey(folders))
        .putBoolean(PREF_MEDIA_CACHE_RESPECT_NOMEDIA, respectNomedia)
        .putInt(PREF_MEDIA_CACHE_SCANNED, scanned)
        .putBoolean(PREF_MEDIA_CACHE_COMPLETE, complete)
        .remove(PREF_MEDIA_CACHE_ITEMS)
        .apply()
}

fun clearCachedMediaScan(context: Context, prefs: SharedPreferences) {
    mediaCacheFile(context).delete()
    File(mediaCacheFile(context).parentFile, "${MEDIA_CACHE_FILE_NAME}.tmp").delete()
    prefs.edit()
        .remove(PREF_MEDIA_CACHE_FOLDERS)
        .remove(PREF_MEDIA_CACHE_RESPECT_NOMEDIA)
        .remove(PREF_MEDIA_CACHE_SCANNED)
        .remove(PREF_MEDIA_CACHE_COMPLETE)
        .remove(PREF_MEDIA_CACHE_ITEMS)
        .apply()
}

fun calculateMediaCacheSizeBytes(context: Context, prefs: SharedPreferences): Long {
    return mediaCacheFile(context).length() +
        (prefs.getString(PREF_MEDIA_CACHE_ITEMS, null)?.length ?: 0).toLong() +
        (prefs.getString(PREF_MEDIA_CACHE_FOLDERS, null)?.length ?: 0).toLong()
}

private fun readMediaItems(reader: JsonReader): List<MediaItem> {
    return buildList {
        reader.beginArray()
        while (reader.hasNext()) {
            var uri: Uri? = null
            var name = "unknown"
            var mimeType = ""
            var size = 0L
            var folderUri: Uri? = null
            var modifiedAt = 0L

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "uri" -> uri = Uri.parse(reader.nextString())
                    "name" -> name = reader.nextString()
                    "mimeType" -> mimeType = reader.nextString()
                    "size" -> size = reader.nextLong()
                    "folderUri" -> folderUri = Uri.parse(reader.nextString())
                    "modifiedAt" -> modifiedAt = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            val itemUri = uri
            val itemFolderUri = folderUri
            if (itemUri != null && itemFolderUri != null) {
                add(
                    MediaItem(
                        uri = itemUri,
                        name = name,
                        mimeType = mimeType,
                        size = size,
                        folderUri = itemFolderUri,
                        modifiedAt = modifiedAt
                    )
                )
            }
        }
        reader.endArray()
    }
}

private fun writeMediaItems(writer: JsonWriter, items: List<MediaItem>) {
    writer.beginArray()
    items.forEach { item ->
        writer.beginObject()
        writer.name("uri").value(item.uriString)
        writer.name("name").value(item.name)
        writer.name("mimeType").value(item.mimeType)
        writer.name("size").value(item.size)
        writer.name("folderUri").value(item.folderUri.toString())
        writer.name("modifiedAt").value(item.modifiedAt)
        writer.endObject()
    }
    writer.endArray()
}
