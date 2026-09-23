package io.github.immersionplayer.desktop.mpv

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_END_FILE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_FILE_LOADED
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_NONE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_PROPERTY_CHANGE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_SHUTDOWN
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.FORMAT_DOUBLE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.FORMAT_FLAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.Semaphore

/**
 * libmpv with the software render API: mpv draws each frame straight into the pixel memory of a
 * Skia bitmap, which Compose then draws. Works the same on every OS, with no GL context to share.
 */
class MpvPlayer {
    private val lib = MpvLib.INSTANCE
    private val handle: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    private val renderContext: Pointer

    data class Frame(val bitmap: Bitmap, val serial: Long)

    private val _frame = MutableStateFlow<Frame?>(null)
    /** The latest rendered frame. A new [Frame.serial] means the pixels changed. */
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _paused = MutableStateFlow(true)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    @Volatile private var targetWidth = 0
    @Volatile private var targetHeight = 0
    @Volatile private var running = true
    private val wake = Semaphore(0)
    // kept referenced so JNA doesn't free the callback while mpv holds it
    private val updateCallback = MpvLib.UpdateCallback { wake.release() }

    init {
        option("vo", "libmpv")
        option("hwdec", "auto-copy-safe")
        option("keep-open", "yes")
        option("sub-auto", "no")
        option("sid", "no") // the app draws subtitles itself
        option("terminal", "no")
        // extra options for experiments: -Dmpv.options=name=value,name=value
        System.getProperty("mpv.options")?.split(',')?.filter { '=' in it }?.forEach {
            option(it.substringBefore('='), it.substringAfter('='))
        }
        check(lib.mpv_initialize(handle) >= 0) { "mpv_initialize failed" }

        val sw = Memory(3).apply { setString(0, "sw") }
        val params = renderParams(MpvLib.RENDER_PARAM_API_TYPE to sw)
        val out = arrayOfNulls<Pointer>(1)
        val result = lib.mpv_render_context_create(out, handle, params)
        check(result >= 0) { "mpv_render_context_create: ${lib.mpv_error_string(result)}" }
        renderContext = out[0]!!
        lib.mpv_render_context_set_update_callback(renderContext, updateCallback, null)

        lib.mpv_observe_property(handle, 1, "time-pos", FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 2, "duration", FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 3, "pause", FORMAT_FLAG)

        Thread(::eventLoop, "mpv-events").apply { isDaemon = true }.start()
        Thread(::renderLoop, "mpv-render").apply { isDaemon = true }.start()
    }

    fun load(path: String) {
        _position.value = 0.0
        _duration.value = 0.0
        command("loadfile", path)
    }
    fun togglePause() = command("cycle", "pause")
    fun setPaused(paused: Boolean) = property("pause", if (paused) "yes" else "no")
    fun seek(seconds: Double) = command("seek", seconds.toString(), "absolute+exact")
    fun seekRelative(seconds: Double) = command("seek", seconds.toString(), "relative+exact")

    /** Size of the area the video is drawn into, in physical pixels. */
    fun setTargetSize(width: Int, height: Int) {
        if (width == targetWidth && height == targetHeight) return
        targetWidth = width
        targetHeight = height
        wake.release()
    }

    fun command(vararg args: String) {
        lib.mpv_command_async(handle, 0, arrayOf(*args, null))
    }

    fun property(name: String, value: String) {
        lib.mpv_set_property_string(handle, name, value)
    }

    /** A property's current value as mpv formats it, or null if it has none. */
    fun propertyString(name: String): String? {
        val value = lib.mpv_get_property_string(handle, name) ?: return null
        return try { value.getString(0) } finally { lib.mpv_free(value) }
    }

    private fun option(name: String, value: String) {
        lib.mpv_set_option_string(handle, name, value)
    }

    fun destroy() {
        running = false
        wake.release()
        lib.mpv_render_context_set_update_callback(renderContext, null, null)
        lib.mpv_render_context_free(renderContext)
        lib.mpv_terminate_destroy(handle)
    }

    // --- rendering -------------------------------------------------------------------------

    private var buffers = arrayOfNulls<Bitmap>(2)
    private var back = 0
    private var serial = 0L
    private val size = Memory(8)
    private val stride = Memory(8)
    private val format = Memory(5).apply { setString(0, "rgb0") }

    private fun renderLoop() {
        var renderedSize = 0 to 0
        while (running) {
            wake.acquire()
            wake.drainPermits()
            if (!running) break
            val flags = lib.mpv_render_context_update(renderContext)
            val width = targetWidth
            val height = targetHeight
            if (width <= 0 || height <= 0) continue
            val resized = renderedSize != width to height
            if (flags and MpvLib.RENDER_UPDATE_FRAME == 0L && !resized) continue
            renderedSize = width to height
            renderFrame(width, height)
        }
    }

    private fun renderFrame(width: Int, height: Int) {
        val bitmap = bufferFor(back, width, height)
        val pixmap = bitmap.peekPixels() ?: return
        size.setInt(0, width)
        size.setInt(4, height)
        stride.setLong(0, pixmap.rowBytes.toLong())
        val params = renderParams(
            MpvLib.RENDER_PARAM_SW_SIZE to size,
            MpvLib.RENDER_PARAM_SW_FORMAT to format,
            MpvLib.RENDER_PARAM_SW_STRIDE to stride,
            MpvLib.RENDER_PARAM_SW_POINTER to Pointer(pixmap.addr),
        )
        // blocks until the frame's display time, which is what paces playback
        lib.mpv_render_context_render(renderContext, params)
        bitmap.notifyPixelsChanged()
        _frame.value = Frame(bitmap, ++serial)
        back = 1 - back
    }

    private fun bufferFor(index: Int, width: Int, height: Int): Bitmap {
        val existing = buffers[index]
        if (existing != null && existing.width == width && existing.height == height) return existing
        // "rgb0" is R,G,B then a byte mpv leaves as garbage (usually 0), which is Skia's RGB_888X.
        // Calling it BGRA_8888 would make that byte alpha and the video transparent.
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE))
        buffers[index] = bitmap
        return bitmap
    }

    /** A zero-terminated mpv_render_param array: { int type; void *data; } per entry. */
    private fun renderParams(vararg entries: Pair<Int, Pointer>): Memory {
        val memory = Memory(16L * (entries.size + 1)).apply { clear() }
        entries.forEachIndexed { i, (type, data) ->
            memory.setInt(16L * i, type)
            memory.setPointer(16L * i + 8, data)
        }
        memory.setInt(16L * entries.size, MpvLib.RENDER_PARAM_INVALID)
        return memory
    }

    // --- events ----------------------------------------------------------------------------

    private fun eventLoop() {
        while (running) {
            // mpv_event: { int event_id; int error; uint64 reply_userdata; void *data; }
            val event = lib.mpv_wait_event(handle, -1.0)
            when (event.getInt(0)) {
                EVENT_NONE -> Unit
                EVENT_SHUTDOWN -> return
                EVENT_PROPERTY_CHANGE -> onProperty(event.getPointer(16))
                EVENT_FILE_LOADED, EVENT_END_FILE -> wake.release()
            }
        }
    }

    private fun onProperty(property: Pointer) {
        // mpv_event_property: { const char *name; mpv_format format; void *data; }
        val name = property.getPointer(0).getString(0)
        val format = property.getInt(8)
        val data = property.getPointer(16) ?: return
        when {
            name == "time-pos" && format == FORMAT_DOUBLE -> _position.value = data.getDouble(0)
            name == "duration" && format == FORMAT_DOUBLE -> _duration.value = data.getDouble(0)
            name == "pause" && format == FORMAT_FLAG -> _paused.value = data.getInt(0) != 0
        }
    }
}
