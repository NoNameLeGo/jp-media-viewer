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
class SubfolderSortingTest {

    private fun item(name: String, size: Long) = MediaItem(
        uri = Uri.parse("content://com.example/$name"),
        name = name,
        mimeType = "image/jpeg",
        size = size,
        folderUri = Uri.parse("content://com.example/folder"),
        modifiedAt = 0
    )

    @Test
    fun `FileName ascending sorts alphabetically case-insensitive`() {
        val items = listOf(item("Banana.jpg", 100), item("apple.jpg", 200), item("cherry.jpg", 50))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileName, descending = false)
        assertEquals(listOf("apple.jpg", "Banana.jpg", "cherry.jpg"), sorted.map { it.name })
    }

    @Test
    fun `FileName descending reverses alphabetical order`() {
        val items = listOf(item("apple.jpg", 100), item("Banana.jpg", 200), item("cherry.jpg", 50))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileName, descending = true)
        assertEquals(listOf("cherry.jpg", "Banana.jpg", "apple.jpg"), sorted.map { it.name })
    }

    @Test
    fun `FileSize ascending sorts by size`() {
        val items = listOf(item("big.jpg", 9000), item("small.jpg", 100), item("mid.jpg", 500))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileSize, descending = false)
        assertEquals(listOf("small.jpg", "mid.jpg", "big.jpg"), sorted.map { it.name })
    }

    @Test
    fun `FileSize descending sorts by size descending`() {
        val items = listOf(item("small.jpg", 100), item("big.jpg", 9000), item("mid.jpg", 500))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileSize, descending = true)
        assertEquals(listOf("big.jpg", "mid.jpg", "small.jpg"), sorted.map { it.name })
    }

    @Test
    fun `Random mode returns items unchanged`() {
        val items = listOf(item("a.jpg", 100), item("b.jpg", 200), item("c.jpg", 300))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.Random, descending = false)
        assertEquals(items.map { it.name }, sorted.map { it.name })
    }

    @Test
    fun `equal primary sort falls back to name`() {
        val items = listOf(item("Banana.jpg", 500), item("apple.jpg", 500))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileSize, descending = false)
        assertEquals(listOf("apple.jpg", "Banana.jpg"), sorted.map { it.name })
    }

    @Test
    fun `empty list returns empty`() {
        val sorted = emptyList<MediaItem>().sortedBySubfolderOrder(SubfolderSortMode.FileName, false)
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun `single item list returns same item`() {
        val items = listOf(item("only.jpg", 100))
        val sorted = items.sortedBySubfolderOrder(SubfolderSortMode.FileName, false)
        assertEquals(1, sorted.size)
        assertEquals("only.jpg", sorted[0].name)
    }
}
