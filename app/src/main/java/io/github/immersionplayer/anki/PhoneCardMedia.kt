package io.github.immersionplayer.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.immersionplayer.Prefs
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * A card's media on the phone: the line's audio cut by Android's codecs, and the frame the
 * player is showing as a WebP. libmpv is one per process and the player has it, so the frame
 * comes from the player itself (which also means no moving picture here, unlike desktop).
 */
class PhoneCardMedia(
    private val context: Context,
    private val prefs: Prefs,
    private val videoUri: String,
    /** mpv's audio track number (1 = the first audio track), or null for the first. */
    private val audioTrack: Int?,
) : CardMedia {
    override val imageExtension = "webp"

    override fun audio(word: MinedWord, out: File): Boolean {
        val padding = prefs.ankiAudioPadding.toDouble()
        return AudioClipper.clip(
            context, Uri.parse(videoUri), word.start - padding, word.end + padding, audioTrack?.minus(1), out,
        )
    }

    override fun image(word: MinedWord, out: File): Boolean {
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
