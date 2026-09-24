package io.github.immersionplayer.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.immersionplayer.Prefs
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * A card's media on the phone: the line's audio cut by Android's codecs, and the line as an
 * animated WebP cut by a second, headless mpv ([LineFrames]); or, as a still, the frame the
 * player is showing.
 */
class PhoneCardMedia(
    private val context: Context,
    private val prefs: Prefs,
    private val videoUri: String,
    /** mpv's audio track number (1 = the first audio track), or null for the first. */
    private val audioTrack: Int?,
) : CardMedia {
    private companion object {
        /** Seconds of a long line that get animated. */
        const val MAX_ANIMATION = 10.0
    }

    override fun audio(word: MinedWord, out: File): Boolean {
        val padding = prefs.ankiAudioPadding.toDouble()
        return AudioClipper.clip(
            context, Uri.parse(videoUri), word.start - padding, word.end + padding, audioTrack?.minus(1), out,
        )
    }

    override fun image(word: MinedWord, base: File): File? {
        if (prefs.ankiImageAnimated) {
            val animation = runCatching {
                LineFrames.animate(
                    context, videoUri, word.start, minOf(word.end, word.start + MAX_ANIMATION),
                    prefs.ankiImageHeight, base,
                )
            }.getOrNull()
            if (animation != null) return animation
            // couldn't decode the line: the still is better than no picture
        }
        val out = File(base.path + ".webp")
        return out.takeIf { still(it) }
    }

    private fun still(out: File): Boolean {
        val shot = File(out.parentFile, out.nameWithoutExtension + ".frame.jpg")
        try {
            MPVLib.setPropertyString("screenshot-format", "jpg")
            MPVLib.setPropertyInt("screenshot-jpeg-quality", 92)
            // "video": the frame alone, without the OSD
            MPVLib.command(arrayOf("screenshot-to-file", shot.path, "video"))
            val frame = BitmapFactory.decodeFile(shot.path) ?: return false
            val height = prefs.ankiImageHeight.coerceAtMost(frame.height)
            val width = (frame.width.toLong() * height / frame.height).toInt()
            val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, it) }
            return out.length() > 0
        } finally {
            shot.delete()
        }
    }
}
