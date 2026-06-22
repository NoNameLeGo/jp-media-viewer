package com.jp.app

import com.jp.app.data.MediaItem
import java.util.Locale

private fun List<MediaItem>.sortedBySubfolderOrderInternal(
    mode: SubfolderSortMode,
    descending: Boolean
): List<MediaItem> {
    if (mode == SubfolderSortMode.Random) return this

    return sortedWith { left, right ->
        val primary = when (mode) {
            SubfolderSortMode.FileName -> left.name.compareTo(right.name, ignoreCase = true)
            SubfolderSortMode.FileSize -> left.size.compareTo(right.size)
            SubfolderSortMode.Random -> 0
        }
        val directedPrimary = if (descending) -primary else primary
        if (directedPrimary != 0) {
            directedPrimary
        } else {
            left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
        }
    }
}

fun List<MediaItem>.sortedBySubfolderOrder(
    mode: SubfolderSortMode,
    descending: Boolean
): List<MediaItem> {
    return sortedBySubfolderOrderInternal(mode, descending)
}
