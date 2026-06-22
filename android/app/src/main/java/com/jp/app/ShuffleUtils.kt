package com.jp.app

import com.jp.app.data.MediaItem

fun reshuffleAvoidingFirst(items: List<MediaItem>, previousFirstUri: String?): List<MediaItem> {
    if (items.size < 2 || previousFirstUri == null) return items.shuffled()

    val shuffled = items.shuffled().toMutableList()
    if (shuffled.firstOrNull()?.uriString != previousFirstUri) return shuffled

    val swapIndex = shuffled.indexOfFirst { it.uriString != previousFirstUri }
    if (swapIndex > 0) {
        val first = shuffled[0]
        shuffled[0] = shuffled[swapIndex]
        shuffled[swapIndex] = first
    }
    return shuffled
}
