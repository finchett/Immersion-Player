package io.github.immersionplayer.player

import android.content.Context
import android.graphics.BitmapFactory
import `is`.xyz.mpv.MPVLib
import io.github.immersionplayer.ui.ThumbnailStore
import java.io.File

/**
 * Makes library thumbnails without playing anything: mpv decodes one frame a fifth of the way in
 * and writes it out as a JPEG (`--vo=image`). Android's own extractors can't decode the 10-bit
 * files this app is mostly used with, so mpv does the work.
 *
 * libmpv is a per-process singleton, so this only runs while no video is open; [MpvOwner]
 * keeps the player and the generator from ever overlapping.
 */
object ThumbnailGenerator {
    @Volatile private var abort = false

    /** Asks a running generation to give up quickly, so the player can have libmpv. */
    fun requestAbort() {
        abort = true
    }

    fun generate(context: Context, videoUri: String, store: ThumbnailStore): Boolean {
        val outputDir = File(context.cacheDir, "thumbgen").apply {
            deleteRecursively()
            mkdirs()
        }
        abort = false
        try {
            MPVLib.create(context.applicationContext)
            MPVLib.setOptionString("config", "no")
            MPVLib.setOptionString("terminal", "no")
            MPVLib.setOptionString("vo", "image")
            MPVLib.setOptionString("vo-image-outdir", outputDir.path)
            MPVLib.setOptionString("vo-image-format", "jpg")
            MPVLib.setOptionString("vo-image-jpeg-quality", "80")
            MPVLib.setOptionString("vf", "scale=480:-2")
            MPVLib.setOptionString("audio", "no")
            MPVLib.setOptionString("sub", "no")
            MPVLib.setOptionString("hwdec", "no")
            MPVLib.setOptionString("start", "20%")
            MPVLib.setOptionString("frames", "1")
            MPVLib.setOptionString("keep-open", "no")
            MPVLib.init()
            MPVLib.command(arrayOf("loadfile", videoUri))

            val written = waitForFrame(outputDir)
            if (written != null) {
                val bitmap = BitmapFactory.decodeFile(written.path) ?: return false
                store.save(videoUri, bitmap)
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { MPVLib.destroy() }
            outputDir.deleteRecursively()
        }
    }

    /** vo=image writes 00000001.jpg once the frame is decoded. */
    private fun waitForFrame(directory: File, timeoutMs: Long = 15_000): File? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !abort) {
            val file = directory.listFiles()?.firstOrNull { it.length() > 0 }
            if (file != null) {
                // let the write finish before reading it
                var size = -1L
                while (size != file.length()) {
                    size = file.length()
                    Thread.sleep(60)
                }
                return file
            }
            Thread.sleep(100)
        }
        return null
    }
}

/** Serialises access to the process-wide libmpv instance. */
object MpvOwner {
    private val lock = Object()
    private var owner: String? = null

    /** Waits for the current owner to finish, then claims libmpv. */
    fun acquire(name: String) {
        synchronized(lock) {
            while (owner != null) lock.wait()
            owner = name
        }
    }

    /** Claims libmpv only if it's free. */
    fun tryAcquire(name: String): Boolean = synchronized(lock) {
        if (owner != null) return false
        owner = name
        true
    }

    fun release(name: String) {
        synchronized(lock) {
            if (owner == name) {
                owner = null
                lock.notifyAll()
            }
        }
    }
}
