package io.github.immersionplayer.desktop

import com.sun.jna.Pointer
import io.github.immersionplayer.desktop.mpv.MpvLib
import java.io.File
import java.util.Locale

/**
 * The sentence audio and screenshot of a card, each cut by its own headless mpv from the video
 * file (not from what the player shows), so the player carries on undisturbed.
 */
object CardMedia {

    /** [start]..[end] of the video's audio as mono Opus in Ogg: about 4 KB a second. */
    fun audioClip(video: File, start: Double, end: Double, audioTrack: String?, out: File): Boolean = run(
        video, out,
        listOf(
            "o" to out.path, "of" to "ogg", "oac" to "libopus", "oacopts" to "b=32k",
            "audio-channels" to "mono", "vid" to "no", "sid" to "no",
            "start" to seconds(start), "end" to seconds(end),
        ) + listOfNotNull(audioTrack?.takeIf { it.all(Char::isDigit) }?.let { "aid" to it }),
    )

    /**
     * The frame at [at], [height] pixels tall, as AVIF: around 10 KB at 360p where a JPEG of the
     * same frame is 50. (The ffmpeg in mpv's builds has no WebP encoder; AVIF shows in Anki's
     * webview and on AnkiDroid and AnkiMobile.)
     */
    fun screenshot(video: File, at: Double, height: Int, out: File): Boolean {
        val dir = File(out.parentFile, out.nameWithoutExtension + ".frames").apply { deleteRecursively(); mkdirs() }
        try {
            run(
                video, null,
                listOf(
                    "vo" to "image", "vo-image-outdir" to dir.path, "vo-image-format" to "avif",
                    "vo-image-avif-opts" to "usage=allintra,crf=36,cpu-used=8",
                    "vf" to "scale=-2:$height", "audio" to "no", "sid" to "no", "hwdec" to "no",
                    "hr-seek" to "yes", "start" to seconds(at), "frames" to "1",
                ),
            )
            val frame = dir.listFiles()?.firstOrNull { it.length() > 0 } ?: return false
            out.delete()
            return frame.renameTo(out)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * [start]..[end] as an animated AVIF, 10 frames a second and [height] pixels tall: about
     * 15–60 KB a second depending on motion, and it plays in Anki like a GIF. Long lines are cut at [MAX_ANIMATION] seconds.
     */
    fun animation(video: File, start: Double, end: Double, height: Int, out: File): Boolean = run(
        video, out,
        listOf(
            "o" to out.path, "of" to "avif", "ovc" to "libaom-av1",
            "ovcopts" to "crf=50,cpu-used=8,usage=realtime,row-mt=1",
            "vf" to "fps=10,scale=-2:$height", "aid" to "no", "sid" to "no", "hwdec" to "no",
            "start" to seconds(start), "end" to seconds(minOf(end, start + MAX_ANIMATION)),
        ),
        timeoutMs = 60_000,
    )

    private const val MAX_ANIMATION = 10.0

    // mpv reads a decimal point whatever the system's locale writes
    private fun seconds(value: Double) = String.format(Locale.ROOT, "%.3f", value.coerceAtLeast(0.0))

    /** Plays [video] headless with [options] until the file ends; true if [out] then has data. */
    private fun run(video: File, out: File?, options: List<Pair<String, String>>, timeoutMs: Long = 20_000): Boolean {
        val lib = MpvLib.INSTANCE
        out?.delete()
        val handle: Pointer = lib.mpv_create() ?: return false
        try {
            (listOf("config" to "no", "terminal" to "no", "keep-open" to "no", "idle" to "no") + options)
                .forEach { (name, value) -> lib.mpv_set_option_string(handle, name, value) }
            if (lib.mpv_initialize(handle) < 0) return false
            lib.mpv_command(handle, arrayOf("loadfile", video.path, null))
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                when (lib.mpv_wait_event(handle, 0.25).getInt(0)) {
                    MpvLib.EVENT_END_FILE, MpvLib.EVENT_SHUTDOWN -> break
                }
            }
        } finally {
            // the encoder writes the end of the file as mpv shuts down
            lib.mpv_terminate_destroy(handle)
        }
        return out == null || out.length() > 0
    }
}
