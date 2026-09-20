package io.github.immersionplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/** Library thumbnails: the frame a video was last left on, grabbed by mpv. */
class ThumbnailStore(context: Context) {
    private val directory = File(context.cacheDir, "thumbnails").apply { mkdirs() }

    fun save(videoUri: String, bitmap: Bitmap) {
        runCatching {
            file(videoUri).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
    }

    fun load(videoUri: String): Bitmap? {
        val file = file(videoUri)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    /** Changes whenever the thumbnail does, so Compose reloads it. */
    fun version(videoUri: String): Long = file(videoUri).lastModified()

    private fun file(videoUri: String) = File(directory, hash(videoUri) + ".jpg")

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
