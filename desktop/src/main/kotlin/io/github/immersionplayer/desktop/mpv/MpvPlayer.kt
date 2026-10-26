package io.github.immersionplayer.desktop.mpv

import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_END_FILE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_FILE_LOADED
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_NONE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_PROPERTY_CHANGE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.EVENT_SHUTDOWN
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.FORMAT_DOUBLE
import io.github.immersionplayer.desktop.mpv.MpvLib.Companion.FORMAT_FLAG
import io.github.immersionplayer.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore

/**
 * libmpv drawing each frame into the pixel memory of a Skia bitmap, which Compose then draws.
 *
 * On macOS mpv uses its GPU renderer in an OpenGL context of the player's own ([OffscreenGl]),
 * and each frame is read back into the bitmap: that's what runs GLSL shaders such as Anime4K.
 * Elsewhere, or if that context can't be made, it falls back to the software render API, which
 * draws straight into the bitmap and works the same on every OS. -Dimmersion.renderer=sw forces
 * the software one.
 */
class MpvPlayer(options: Map<String, String> = emptyMap()) {
    private val lib = MpvLib.INSTANCE
    private val handle: Pointer = lib.mpv_create() ?: error("mpv_create failed")
    // both made on the render thread, which GL requires to be the one that uses them
    private lateinit var renderContext: Pointer
    private var gl: OffscreenGl? = null
    private val renderThread: Thread

    /** Whether mpv renders on the GPU, so [setShaders] has an effect. */
    val runsShaders: Boolean get() = gl != null

    /** The GPU mpv renders on, or null with the software renderer. */
    val gpu: String? get() = gl?.renderer

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

    private val _ended = MutableStateFlow(false)
    /** Whether playback has reached the end of the file (and, with keep-open, holds the last frame). */
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    /** The loaded file's audio tracks, read when it loads. */
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _audioTrack = MutableStateFlow<Int?>(null)
    /** The [AudioTrack.id] playing, or null for none. */
    val audioTrack: StateFlow<Int?> = _audioTrack.asStateFlow()

    @Volatile private var targetWidth = 0
    @Volatile private var targetHeight = 0
    @Volatile private var running = true
    private val wake = Semaphore(0)
    // kept referenced so JNA doesn't free the callback while mpv holds it
    private val updateCallback = MpvLib.UpdateCallback { wake.release() }
    private val getProcAddress = MpvLib.GetProcAddress { _, name -> gl?.procAddress(name) }

    init {
        option("vo", "libmpv")
        option("hwdec", "auto-copy-safe")
        option("keep-open", "yes")
        option("sub-auto", "no")
        option("sid", "no") // the app draws subtitles itself
        option("terminal", "no")
        options.forEach { (name, value) -> option(name, value) }
        // extra options for experiments: -Dmpv.options=name=value,name=value
        System.getProperty("mpv.options")?.split(',')?.filter { '=' in it }?.forEach {
            option(it.substringBefore('='), it.substringAfter('='))
        }
        check(lib.mpv_initialize(handle) >= 0) { "mpv_initialize failed" }

        lib.mpv_observe_property(handle, 1, "time-pos", FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 2, "duration", FORMAT_DOUBLE)
        lib.mpv_observe_property(handle, 3, "pause", FORMAT_FLAG)
        lib.mpv_observe_property(handle, 4, "eof-reached", FORMAT_FLAG)

        Thread(::eventLoop, "mpv-events").apply { isDaemon = true }.start()
        val created = CompletableFuture<Unit>()
        renderThread = Thread({
            try {
                createRenderContext()
                created.complete(Unit)
            } catch (e: Throwable) {
                created.completeExceptionally(e)
                return@Thread
            }
            renderLoop()
            lib.mpv_render_context_set_update_callback(renderContext, null, null)
            lib.mpv_render_context_free(renderContext)
            gl?.destroy()
        }, "mpv-render").apply { isDaemon = true }
        renderThread.start()
        try {
            created.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun createRenderContext() {
        if (System.getProperty("immersion.renderer") != "sw") {
            OffscreenGl.create()?.let { context ->
                gl = context
                val initParams = Memory(16).apply {
                    setPointer(0, CallbackReference.getFunctionPointer(getProcAddress))
                    setPointer(8, null)
                }
                val created = createRenderContext(
                    MpvLib.RENDER_PARAM_API_TYPE to cString("opengl"),
                    MpvLib.RENDER_PARAM_OPENGL_INIT_PARAMS to initParams,
                )
                if (created == null) {
                    gl = null
                    context.destroy()
                }
            }
        }
        if (gl == null) {
            createRenderContext(MpvLib.RENDER_PARAM_API_TYPE to cString("sw"))
                ?: error("mpv_render_context_create failed")
        }
        lib.mpv_render_context_set_update_callback(renderContext, updateCallback, null)
    }

    /** Sets [renderContext] and returns it, or returns null if mpv refuses the [params]. */
    private fun createRenderContext(vararg params: Pair<Int, Pointer>): Pointer? {
        val out = arrayOfNulls<Pointer>(1)
        val result = lib.mpv_render_context_create(out, handle, renderParams(*params))
        if (result < 0) {
            System.err.println("mpv_render_context_create: ${lib.mpv_error_string(result)}")
            return null
        }
        return out[0]!!.also { renderContext = it }
    }

    private fun cString(value: String) = Memory(value.length + 1L).apply { setString(0, value) }

    fun load(path: String) {
        _position.value = 0.0
        _duration.value = 0.0
        _ended.value = false
        _audioTracks.value = emptyList()
        _audioTrack.value = null
        command("loadfile", path)
    }
    fun togglePause() = command("cycle", "pause")
    fun setPaused(paused: Boolean) = property("pause", if (paused) "yes" else "no")
    fun seek(seconds: Double) = command("seek", seconds.toString(), "absolute+exact")
    fun seekRelative(seconds: Double) = command("seek", seconds.toString(), "relative+exact")

    /** GLSL shaders for mpv's GPU renderer to run, in order; empty for none. */
    fun setShaders(shaders: List<File>) {
        property("glsl-shaders", shaders.joinToString(File.pathSeparator) { it.path })
    }

    fun selectAudio(id: Int) {
        property("aid", id.toString())
        _audioTrack.value = id
    }

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
        // the render thread frees the render context (and GL) on its way out
        renderThread.join()
        lib.mpv_terminate_destroy(handle)
    }

    // --- rendering -------------------------------------------------------------------------

    private var buffers = arrayOfNulls<Bitmap>(2)
    private var back = 0
    private var serial = 0L
    private val size = Memory(8)
    private val stride = Memory(8)
    private val format = cString("rgb0")
    private val fbo = Memory(16)

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
        val gl = gl
        if (gl != null) {
            // mpv_opengl_fbo: { int fbo; int w, h; int internal_format; }
            fbo.setInt(0, gl.framebuffer(width, height))
            fbo.setInt(4, width)
            fbo.setInt(8, height)
            fbo.setInt(12, 0)
            // not flipped: mpv then draws the top row at y = 0, which glReadPixels returns first,
            // the order Skia has rows in
            val params = renderParams(MpvLib.RENDER_PARAM_OPENGL_FBO to fbo)
            lib.mpv_render_context_render(renderContext, params)
            gl.read(Pointer(pixmap.addr), pixmap.rowBytes)
        } else {
            renderSoftware(pixmap, width, height)
        }
        bitmap.notifyPixelsChanged()
        _frame.value = Frame(bitmap, ++serial)
        back = 1 - back
    }

    private fun renderSoftware(pixmap: Pixmap, width: Int, height: Int) {
        size.setInt(0, width)
        size.setInt(4, height)
        stride.setLong(0, pixmap.rowBytes.toLong())
        val params = renderParams(
            MpvLib.RENDER_PARAM_SW_SIZE to size,
            MpvLib.RENDER_PARAM_SW_FORMAT to format,
            MpvLib.RENDER_PARAM_SW_STRIDE to stride,
            MpvLib.RENDER_PARAM_SW_POINTER to Pointer(pixmap.addr),
        )
        // blocks until the frame's display time, which is what paces playback (with GL too)
        lib.mpv_render_context_render(renderContext, params)
    }

    private fun bufferFor(index: Int, width: Int, height: Int): Bitmap {
        val existing = buffers[index]
        if (existing != null && existing.width == width && existing.height == height) return existing
        // "rgb0" is R,G,B then a byte mpv leaves as garbage (usually 0), which is Skia's RGB_888X.
        // Calling it BGRA_8888 would make that byte alpha and the video transparent.
        // GL is read back as BGRA, with mpv's (opaque) alpha.
        val colorType = if (gl != null) ColorType.BGRA_8888 else ColorType.RGB_888X
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, colorType, ColorAlphaType.OPAQUE))
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
                EVENT_FILE_LOADED -> {
                    _audioTracks.value = AudioTrack.fromMpv(::propertyString)
                    _audioTrack.value = propertyString("aid")?.toIntOrNull()
                    wake.release()
                }
                EVENT_END_FILE -> wake.release()
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
            name == "eof-reached" && format == FORMAT_FLAG -> _ended.value = data.getInt(0) != 0
        }
    }
}
