package io.github.immersionplayer.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.Locale

/**
 * The frames of a stretch of video, decoded by a second, headless libmpv while the player keeps
 * playing. mpv-android's JNI wrapper holds a single mpv, but libmpv itself allows any number, so
 * this one is driven through JNA as on desktop. The app's ffmpeg can only encode JPEG and PNG,
 * so the frames come out as JPEGs and are made into an animation with Android's own encoders.
 */
object LineFrames {

    private const val FPS = 10

    /**
     * [start]..[end] seconds of [video] animated at [FPS] frames a second and [height] pixels
     * tall, written next to [base]: an AVIF from the phone's AV1 encoder where it has one (about
     * 20–60 KB a second), otherwise an animated WebP. Null if mpv couldn't decode the line.
     */
    fun animate(context: Context, video: String, start: Double, end: Double, height: Int, base: File): File? {
        val dir = File(base.parentFile, base.name + ".frames").apply { deleteRecursively(); mkdirs() }
        val started = System.currentTimeMillis()
        val report = StringBuilder()
        fun step(what: String) = report.append("$what at ${System.currentTimeMillis() - started} ms; ")
        try {
            openVideo(context, video) { path ->
                run(
                    path,
                    listOf(
                        "vo" to "image", "vo-image-outdir" to dir.path,
                        "vo-image-format" to "jpg", "vo-image-jpeg-quality" to "90",
                        "vf" to "fps=$FPS,scale=-2:$height", "aid" to "no", "sid" to "no", "hwdec" to "no",
                        // the same dither on every frame, so a still background stays identical
                        // (mpv's default for 10-bit video is random)
                        "zimg-dither" to "ordered",
                        "hr-seek" to "yes", "start" to seconds(start), "end" to seconds(end),
                    ),
                )
            }
            val frames = dir.listFiles().orEmpty().filter { it.length() > 0 }.sortedBy { it.name }
            step("${frames.size} frames decoded")
            if (frames.isEmpty()) return null
            if (AnimatedAvif.supported()) {
                val avif = File(base.path + ".avif")
                val made = runCatching { avif(frames, avif) }.onFailure { step("AVIF failed: $it") }.getOrDefault(false)
                step("AVIF ${if (made) "made" else "not made"}")
                if (made) return avif
                avif.delete()
            }
            val webp = File(base.path + ".webp")
            return webp.takeIf { webp(frames, it) }.also { step("WebP") }
        } finally {
            lastReport = report.toString()
            dir.deleteRecursively()
        }
    }

    /** How long each step of the last [animate] took, and why AVIF failed if it did. */
    internal var lastReport = ""

    private fun avif(frames: List<File>, out: File): Boolean {
        var encoder: AnimatedAvif? = null
        try {
            for (file in frames) {
                val bitmap = BitmapFactory.decodeFile(file.path) ?: continue
                val animation = encoder ?: AnimatedAvif(out, bitmap.width, bitmap.height, FPS).also { encoder = it }
                // the encoder is set up for the first frame's size
                if (bitmap.width == animation.width && bitmap.height == animation.height) animation.add(bitmap)
                bitmap.recycle()
            }
            return encoder?.finish() ?: false
        } catch (e: Exception) {
            encoder?.release()
            throw e
        }
    }

    /** An animated WebP of [frames], each storing only the area that changed since the one before. */
    private fun webp(frames: List<File>, out: File): Boolean {
        val webp = AnimatedWebp(quality = 60)
        // what the animation shows so far, to compare each new frame with
        var canvas: Bitmap? = null
        // one bitmap at a time: a long line at 360p would otherwise be close to 100 MB
        for (file in frames) {
            val frame = BitmapFactory.decodeFile(file.path)?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
            val shown = canvas
            if (shown == null || shown.width != frame.width || shown.height != frame.height) {
                webp.add(frame, 1000 / FPS)
                canvas?.recycle()
                canvas = frame
                continue
            }
            // only the part that changed goes in: in a still shot, the mouth
            val changed = changedArea(shown, frame)
            if (changed == null) {
                webp.extendLast(1000 / FPS)
            } else {
                val part = Bitmap.createBitmap(frame, changed.left, changed.top, changed.width(), changed.height())
                webp.add(part, 1000 / FPS, changed.left, changed.top)
                val pixels = IntArray(changed.width() * changed.height())
                part.getPixels(pixels, 0, changed.width(), 0, 0, changed.width(), changed.height())
                shown.setPixels(pixels, 0, changed.width(), changed.left, changed.top, changed.width(), changed.height())
                if (part !== frame) part.recycle()
            }
            frame.recycle()
        }
        canvas?.recycle()
        if (webp.isEmpty) return false
        out.writeBytes(webp.build())
        return true
    }

    private const val BLOCK = 16

    /** Average difference (0–255) in a block over which it has changed, rather than being noise. */
    private const val CHANGED = 5

    /**
     * The smallest area, on even coordinates, covering every 16×16 block of [frame] that differs
     * from [shown]; null if none does.
     */
    private fun changedArea(shown: Bitmap, frame: Bitmap): Rect? {
        val width = frame.width
        val height = frame.height
        val a = IntArray(width * height).also { shown.getPixels(it, 0, width, 0, 0, width, height) }
        val b = IntArray(width * height).also { frame.getPixels(it, 0, width, 0, 0, width, height) }
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (by in 0 until height step BLOCK) {
            for (bx in 0 until width step BLOCK) {
                val x1 = minOf(bx + BLOCK, width)
                val y1 = minOf(by + BLOCK, height)
                var sum = 0L
                for (y in by until y1) {
                    val row = y * width
                    for (x in bx until x1) sum += luma(a[row + x]).let { kotlin.math.abs(it - luma(b[row + x])) }
                }
                if (sum > CHANGED.toLong() * (x1 - bx) * (y1 - by)) {
                    left = minOf(left, bx); top = minOf(top, by)
                    right = maxOf(right, x1); bottom = maxOf(bottom, y1)
                }
            }
        }
        if (right == 0) return null
        // WebP places frames at even offsets
        return Rect(left and 1.inv(), top and 1.inv(), right, bottom)
    }

    private fun luma(p: Int) = ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10

    /** mpv gets a descriptor for content:// videos, opened here, rather than the player's content access. */
    private fun <T> openVideo(context: Context, video: String, block: (String) -> T): T {
        val uri = Uri.parse(video)
        if (uri.scheme != "content") return block(uri.path ?: video)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("can't open $video")
        return descriptor.use { block("fd://${it.fd}") }
    }

    // mpv reads a decimal point whatever the locale writes
    private fun seconds(value: Double) = String.format(Locale.ROOT, "%.3f", value.coerceAtLeast(0.0))

    /** Plays [path] headless with [options] until the file ends. */
    private fun run(path: String, options: List<Pair<String, String>>, timeoutMs: Long = 30_000) {
        val lib = Mpv.INSTANCE
        val handle = lib.mpv_create() ?: error("mpv_create failed")
        try {
            (listOf("config" to "no", "terminal" to "no", "keep-open" to "no", "idle" to "no") + options)
                .forEach { (name, value) -> lib.mpv_set_option_string(handle, name, value) }
            check(lib.mpv_initialize(handle) >= 0) { "mpv_initialize failed" }
            lib.mpv_command(handle, arrayOf("loadfile", path, null))
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                when (lib.mpv_wait_event(handle, 0.25).getInt(0)) {
                    EVENT_SHUTDOWN, EVENT_END_FILE -> break
                }
            }
        } finally {
            lib.mpv_terminate_destroy(handle)
        }
    }

    private const val EVENT_SHUTDOWN = 1
    private const val EVENT_END_FILE = 7

    /** The few libmpv client calls a headless run needs; the library is the one the player loaded. */
    @Suppress("FunctionName")
    private interface Mpv : Library {
        fun mpv_create(): Pointer?
        fun mpv_initialize(handle: Pointer): Int
        fun mpv_terminate_destroy(handle: Pointer)
        fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
        fun mpv_command(handle: Pointer, args: Array<String?>): Int
        fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer

        companion object {
            val INSTANCE: Mpv by lazy { Native.load("mpv", Mpv::class.java) }
        }
    }
}
