package com.jp.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jp.app.data.MediaItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaCacheTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test", Context.MODE_PRIVATE)
        clearCachedMediaScan(context, prefs)
    }

    private fun item(uri: String, name: String = "img.jpg", size: Long = 1000) = MediaItem(
        uri = Uri.parse(uri),
        name = name,
        mimeType = "image/jpeg",
        size = size,
        folderUri = Uri.parse("content://com.example/folder"),
        modifiedAt = 1700000000000
    )

    // ── mediaCacheFoldersKey ──────────────────────────────────

    @Test
    fun `mediaCacheFoldersKey sorts folders`() {
        val key = mediaCacheFoldersKey(listOf("b", "a", "c"))
        assertEquals("a\nb\nc", key)
    }

    @Test
    fun `mediaCacheFoldersKey is idempotent for sorted input`() {
        val key = mediaCacheFoldersKey(listOf("a", "b"))
        assertEquals("a\nb", key)
    }

    @Test
    fun `mediaCacheFoldersKey empty list`() {
        assertEquals("", mediaCacheFoldersKey(emptyList()))
    }

    // ── save/load round-trip ──────────────────────────────────

    @Test
    fun `save and load round-trip with complete scan`() {
        val folders = listOf("content://com.example/f1", "content://com.example/f2")
        val items = listOf(
            item("content://com.example/f1/a.jpg", "a.jpg", 100),
            item("content://com.example/f2/b.jpg", "b.jpg", 200)
        )

        saveCachedMediaScan(context, prefs, folders, true, items, scanned = 10, complete = true)
        val loaded = loadCachedMediaScan(context, prefs, folders, true)

        assertNotNull(loaded)
        assertEquals(2, loaded!!.items.size)
        assertEquals(10, loaded.scanned)
        assertTrue(loaded.complete)
        assertEquals("a.jpg", loaded.items[0].name)
        assertEquals("b.jpg", loaded.items[1].name)
    }

    @Test
    fun `save and load round-trip with partial scan`() {
        val folders = listOf("content://com.example/f1")
        val items = listOf(item("content://com.example/f1/a.jpg"))

        saveCachedMediaScan(context, prefs, folders, true, items, scanned = 5, complete = false)
        val loaded = loadCachedMediaScan(context, prefs, folders, true)

        assertNotNull(loaded)
        assertEquals(1, loaded!!.items.size)
        assertEquals(5, loaded.scanned)
        assertFalse(loaded.complete)
    }

    @Test
    fun `load returns null when folders differ`() {
        val folders = listOf("content://com.example/f1")
        val items = listOf(item("content://com.example/f1/a.jpg"))

        saveCachedMediaScan(context, prefs, folders, true, items, scanned = 1, complete = true)
        val loaded = loadCachedMediaScan(context, prefs, listOf("content://com.example/other"), true)

        assertNull(loaded)
    }

    @Test
    fun `load returns null when respectNomedia differs`() {
        val folders = listOf("content://com.example/f1")
        val items = listOf(item("content://com.example/f1/a.jpg"))

        saveCachedMediaScan(context, prefs, folders, true, items, scanned = 1, complete = true)
        val loaded = loadCachedMediaScan(context, prefs, folders, false)

        assertNull(loaded)
    }

    @Test
    fun `load returns null when no cache exists`() {
        assertNull(loadCachedMediaScan(context, prefs, listOf("x"), true))
    }

    @Test
    fun `round-trip preserves media fields`() {
        val folders = listOf("content://com.example/f1")
        val original = MediaItem(
            uri = Uri.parse("content://com.example/f1/photo.jpg"),
            name = "photo.jpg",
            mimeType = "image/png",
            size = 12345,
            folderUri = Uri.parse("content://com.example/f1"),
            modifiedAt = 1700000000000
        )

        saveCachedMediaScan(context, prefs, folders, true, listOf(original), scanned = 1, complete = true)
        val loaded = loadCachedMediaScan(context, prefs, folders, true)!!
        val restored = loaded.items[0]

        assertEquals(original.uri, restored.uri)
        assertEquals(original.name, restored.name)
        assertEquals(original.mimeType, restored.mimeType)
        assertEquals(original.size, restored.size)
        assertEquals(original.folderUri, restored.folderUri)
        assertEquals(original.modifiedAt, restored.modifiedAt)
    }

    // ── clearCachedMediaScan ──────────────────────────────────

    @Test
    fun `clear removes cache`() {
        val folders = listOf("content://com.example/f1")
        saveCachedMediaScan(context, prefs, folders, true, listOf(item("content://a/1")), 1, true)

        clearCachedMediaScan(context, prefs)
        assertNull(loadCachedMediaScan(context, prefs, folders, true))
    }

    // ── folder order independence ─────────────────────────────

    @Test
    fun `save with different folder order still loads`() {
        val folders1 = listOf("content://a", "content://b")
        val folders2 = listOf("content://b", "content://a")
        val items = listOf(item("content://a/1.jpg"))

        saveCachedMediaScan(context, prefs, folders1, true, items, 1, true)
        assertNotNull(loadCachedMediaScan(context, prefs, folders2, true))
    }
}
