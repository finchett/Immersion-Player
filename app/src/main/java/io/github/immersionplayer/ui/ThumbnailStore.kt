package io.github.immersionplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Library thumbnails: a frame from each video, decoded by mpv. */
class ThumbnailStore(context: Context) {
    private val directory = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    /** Decoded thumbnails, so scrolling never re-decodes or touches the disk. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Bumped when a thumbnail changes, so cards know to reload. Kept in memory: no disk I/O in layout. */
    private val versions = ConcurrentHashMap<String, Long>()

    fun save(videoUri: String, bitmap: Bitmap) {
        runCatching {
            file(videoUri).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            cache.put(videoUri, bitmap)
            versions[videoUri] = System.currentTimeMillis()
        }
    }

    /** Cached bitmap if there is one; call [load] off the main thread otherwise. */
    fun cached(videoUri: String): Bitmap? = cache.get(videoUri)

    fun load(videoUri: String): Bitmap? {
        cache.get(videoUri)?.let { return it }
        val file = file(videoUri)
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        cache.put(videoUri, bitmap)
        return bitmap
    }

    fun version(videoUri: String): Long = versions[videoUri] ?: 0L

    /** Whether a thumbnail exists, for deciding what still needs generating (off the main thread). */
    fun exists(videoUri: String): Boolean = cache.get(videoUri) != null || file(videoUri).exists()

    private fun file(videoUri: String) = File(directory, hash(videoUri) + ".jpg")

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
