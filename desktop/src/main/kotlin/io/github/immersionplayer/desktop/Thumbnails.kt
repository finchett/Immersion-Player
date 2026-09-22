package io.github.immersionplayer.desktop

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Pointer
import io.github.immersionplayer.desktop.mpv.MpvLib
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Library thumbnails: one frame per video, decoded by a headless mpv (`--vo=image`) on a single
 * background thread and kept as JPEGs in the cache. Unlike Android, libmpv allows several
 * handles per process, so this never has to wait for the player.
 */
class Thumbnails(cacheDir: File) {
    private val directory = File(cacheDir, "thumbnails").apply { mkdirs() }
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "thumbnails").apply { isDaemon = true } }
    private val queued = HashSet<String>()
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 200
    }

    /** Bumped when a video's thumbnail appears, so only that card recomposes. */
    val versions = mutableStateMapOf<String, Long>()

    /** The thumbnail if it is in memory; otherwise null, and [request] will fetch or make it. */
    fun cached(video: File): ImageBitmap? = synchronized(memory) { memory[video.path] }

    /** Loads [video]'s thumbnail from disk, or generates it from [start] (an mpv position). */
    fun request(video: File, start: String) {
        synchronized(queued) { if (!queued.add(video.path)) return }
        worker.execute {
            try {
                val file = fileFor(video)
                if (!file.exists()) generate(video, file, start)
                val image = runCatching { Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }.getOrNull()
                    ?: return@execute
                synchronized(memory) { memory[video.path] = image }
                Snapshot.withMutableSnapshot { versions[video.path] = System.nanoTime() }
            } finally {
                synchronized(queued) { queued.remove(video.path) }
            }
        }
    }

    // a replaced or re-encoded file gets a new thumbnail
    private fun fileFor(video: File) = File(directory, hash("${video.path}|${video.length()}|${video.lastModified()}") + ".jpg")

    private fun generate(video: File, out: File, start: String) {
        val lib = MpvLib.INSTANCE
        val outputDir = File(directory, "gen").apply { deleteRecursively(); mkdirs() }
        val handle: Pointer = lib.mpv_create() ?: return
        try {
            listOf(
                "config" to "no", "terminal" to "no",
                "vo" to "image", "vo-image-outdir" to outputDir.path,
                "vo-image-format" to "jpg", "vo-image-jpeg-quality" to "82",
                "vf" to "scale=480:-2", "audio" to "no", "sub" to "no",
                "hwdec" to "no", "start" to start, "frames" to "1", "keep-open" to "no",
            ).forEach { (name, value) -> lib.mpv_set_option_string(handle, name, value) }
            if (lib.mpv_initialize(handle) < 0) return
            lib.mpv_command(handle, arrayOf("loadfile", video.path, null))
            val frame = waitForFrame(outputDir) ?: return
            frame.renameTo(out)
        } finally {
            lib.mpv_terminate_destroy(handle)
            outputDir.deleteRecursively()
        }
    }

    /** vo=image writes 00000001.jpg once the frame is decoded. */
    private fun waitForFrame(dir: File, timeoutMs: Long = 15_000): File? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val file = dir.listFiles()?.firstOrNull { it.length() > 0 }
            if (file != null) {
                // let the write finish before reading it
                var size = -1L
                while (size != file.length()) {
                    size = file.length()
                    Thread.sleep(60)
                }
                return file
            }
            Thread.sleep(80)
        }
        return null
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
