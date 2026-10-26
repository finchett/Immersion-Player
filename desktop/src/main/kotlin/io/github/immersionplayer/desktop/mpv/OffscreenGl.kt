package io.github.immersionplayer.desktop.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * An OpenGL context of its own with no window (CGL, so macOS only), and a framebuffer the size of
 * the video area for mpv to draw into. Lets mpv use its GPU renderer, which is what runs GLSL
 * shaders such as Anime4K; each frame is read back into memory for Compose to draw.
 *
 * Everything here has to run on the one thread the context was made current on.
 */
internal class OffscreenGl private constructor(private val context: Pointer) {
    /** The GPU's name as the driver gives it, such as "Apple M1 Pro". */
    val renderer: String = gl.glGetString(GL_RENDERER) ?: "unknown"

    private var fbo = 0
    private var texture = 0
    var width = 0
        private set
    var height = 0
        private set

    /** Looks up a GL function for mpv: OpenGL.framework exports all of them directly. */
    fun procAddress(name: String): Pointer? =
        runCatching { framework.getFunction(name) }.getOrNull()

    /** The framebuffer to render into, (re)made at [w] × [h]. */
    fun framebuffer(w: Int, h: Int): Int {
        if (fbo != 0 && w == width && h == height) return fbo
        deleteFramebuffer()
        val ids = IntArray(1)
        gl.glGenTextures(1, ids)
        texture = ids[0]
        gl.glBindTexture(GL_TEXTURE_2D, texture)
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, null)
        gl.glBindTexture(GL_TEXTURE_2D, 0)
        gl.glGenFramebuffers(1, ids)
        fbo = ids[0]
        gl.glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        gl.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
        val status = gl.glCheckFramebufferStatus(GL_FRAMEBUFFER)
        gl.glBindFramebuffer(GL_FRAMEBUFFER, 0)
        check(status == GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete: 0x${status.toString(16)}" }
        width = w
        height = h
        return fbo
    }

    /** Copies the framebuffer into [pixels]: BGRA, top row first, [rowBytes] apart. */
    fun read(pixels: Pointer, rowBytes: Int) {
        gl.glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        gl.glPixelStorei(GL_PACK_ALIGNMENT, 4)
        gl.glPixelStorei(GL_PACK_ROW_LENGTH, rowBytes / 4)
        // BGRA as 8_8_8_8_REV is the layout Apple's drivers copy without converting
        gl.glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels)
        gl.glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun destroy() {
        deleteFramebuffer()
        cgl.CGLSetCurrentContext(null)
        cgl.CGLDestroyContext(context)
    }

    private fun deleteFramebuffer() {
        if (fbo != 0) gl.glDeleteFramebuffers(1, intArrayOf(fbo))
        if (texture != 0) gl.glDeleteTextures(1, intArrayOf(texture))
        fbo = 0
        texture = 0
    }

    companion object {
        private val framework by lazy { NativeLibrary.getInstance("OpenGL") }
        private val cgl by lazy { Native.load("OpenGL", Cgl::class.java) }
        private val gl by lazy { Native.load("OpenGL", Gl::class.java) }

        /** The GPU's name ([renderer]), or null where there's no context to ask. */
        fun gpuName(): String? = create()?.let { context ->
            try { context.renderer } finally { context.destroy() }
        }

        /** A context made current on the calling thread, or null where there's no CGL. */
        fun create(): OffscreenGl? {
            if (!Platform.isMac()) return null
            // 4.1 core is the newest macOS has; 3.2 core is the fallback mpv also accepts
            val context = listOf(GL4_CORE, GL3_2_CORE).firstNotNullOfOrNull(::context) ?: return null
            if (cgl.CGLSetCurrentContext(context) != 0) {
                cgl.CGLDestroyContext(context)
                return null
            }
            return OffscreenGl(context)
        }

        private fun context(version: Int): Pointer? {
            val attributes = intArrayOf(
                PFA_OPENGL_PROFILE, version,
                PFA_ACCELERATED,
                PFA_ALLOW_OFFLINE_RENDERERS,
                PFA_COLOR_SIZE, 24,
                PFA_ALPHA_SIZE, 8,
                0,
            )
            val format = PointerByReference()
            val count = IntByReference()
            if (cgl.CGLChoosePixelFormat(attributes, format, count) != 0 || format.value == null) return null
            val context = PointerByReference()
            val error = cgl.CGLCreateContext(format.value, null, context)
            cgl.CGLDestroyPixelFormat(format.value)
            return context.value.takeIf { error == 0 }
        }

        private const val PFA_COLOR_SIZE = 8
        private const val PFA_ALPHA_SIZE = 11
        private const val PFA_ACCELERATED = 73
        private const val PFA_ALLOW_OFFLINE_RENDERERS = 96
        private const val PFA_OPENGL_PROFILE = 99
        private const val GL3_2_CORE = 0x3200
        private const val GL4_CORE = 0x4100

        private const val GL_RENDERER = 0x1F01
        private const val GL_TEXTURE_2D = 0x0DE1
        private const val GL_TEXTURE_MAG_FILTER = 0x2800
        private const val GL_TEXTURE_MIN_FILTER = 0x2801
        private const val GL_NEAREST = 0x2600
        private const val GL_RGBA8 = 0x8058
        private const val GL_BGRA = 0x80E1
        private const val GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367
        private const val GL_FRAMEBUFFER = 0x8D40
        private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        private const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
        private const val GL_PACK_ROW_LENGTH = 0x0D02
        private const val GL_PACK_ALIGNMENT = 0x0D05
    }
}

@Suppress("FunctionName")
private interface Cgl : Library {
    fun CGLChoosePixelFormat(attributes: IntArray, format: PointerByReference, count: IntByReference): Int
    fun CGLDestroyPixelFormat(format: Pointer): Int
    fun CGLCreateContext(format: Pointer, share: Pointer?, context: PointerByReference): Int
    fun CGLSetCurrentContext(context: Pointer?): Int
    fun CGLDestroyContext(context: Pointer): Int
}

@Suppress("FunctionName")
private interface Gl : Library {
    fun glGetString(name: Int): String?
    fun glGenTextures(n: Int, ids: IntArray)
    fun glDeleteTextures(n: Int, ids: IntArray)
    fun glBindTexture(target: Int, texture: Int)
    fun glTexParameteri(target: Int, name: Int, value: Int)
    fun glTexImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: Pointer?)
    fun glGenFramebuffers(n: Int, ids: IntArray)
    fun glDeleteFramebuffers(n: Int, ids: IntArray)
    fun glBindFramebuffer(target: Int, fbo: Int)
    fun glFramebufferTexture2D(target: Int, attachment: Int, textureTarget: Int, texture: Int, level: Int)
    fun glCheckFramebufferStatus(target: Int): Int
    fun glPixelStorei(name: Int, value: Int)
    fun glReadPixels(x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, pixels: Pointer)
}
