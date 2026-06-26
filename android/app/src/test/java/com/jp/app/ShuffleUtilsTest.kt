package com.jp.app

import android.net.Uri
import com.jp.app.data.MediaItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShuffleUtilsTest {

    private fun item(uri: String, name: String = "file") = MediaItem(
        uri = Uri.parse(uri),
        name = name,
        mimeType = "image/jpeg",
        size = 1000,
        folderUri = Uri.parse("content://com.example/folder"),
        modifiedAt = 0
    )

    @Test
    fun `empty list returns empty`() {
        assertTrue(reshuffleAvoidingFirst(emptyList(), null).isEmpty())
    }

    @Test
    fun `single item list returns same item`() {
        val items = listOf(item("content://a/1"))
        val result = reshuffleAvoidingFirst(items, "content://a/1")
        assertEquals(1, result.size)
        assertEquals("content://a/1", result[0].uriString)
    }

    @Test
    fun `null previousFirstUri returns shuffled`() {
        val items = listOf(item("content://a/1"), item("content://a/2"))
        val result = reshuffleAvoidingFirst(items, null)
        assertEquals(2, result.size)
    }

    @Test
    fun `result does not start with previousFirstUri when possible`() {
        val items = (1..10).map { item("content://a/$it", "file$it") }
        val previousUri = "content://a/1"

        // Run multiple times to account for randomness
        var avoidedCount = 0
        repeat(50) {
            val result = reshuffleAvoidingFirst(items, previousUri)
            if (result[0].uriString != previousUri) avoidedCount++
        }
        // Should avoid previous first in most runs (statistically almost all)
        assertTrue("Expected most shuffles to avoid previous first", avoidedCount > 40)
    }

    @Test
    fun `result contains all items`() {
        val items = (1..5).map { item("content://a/$it", "file$it") }
        val result = reshuffleAvoidingFirst(items, "content://a/1")
        assertEquals(items.map { it.uriString }.toSet(), result.map { it.uriString }.toSet())
    }
}
