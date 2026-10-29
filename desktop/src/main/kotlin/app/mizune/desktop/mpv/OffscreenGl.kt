package app.mizune.desktop.mpv

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * An OpenGL context of its own with no visible window, and a framebuffer the size of the video
 * area for mpv to draw into. Lets mpv use its GPU renderer, which is what runs GLSL shaders such
 * as Anime4K; each frame is read back into memory for Compose to draw.
 *
 * The context comes from each OS's own API: CGL on macOS, WGL (on a hidden window, which Windows
 * needs) on Windows, EGL (a 1×1 pbuffer, or none) on Linux.
 *
 * Everything here has to run on the one thread the context was made current on.
 */
internal class OffscreenGl private constructor(private val context: GlContext) {
    private val gl = Gl(context::procAddress)

    /** The GPU's name as the driver gives it, such as "Apple M1 Pro". */
    val renderer: String = gl.glGetString(GL_RENDERER) ?: "unknown"

    private var fbo = 0
    private var texture = 0
    var width = 0
        private set
    var height = 0
        private set

    /** Looks up a GL function, for mpv as much as for this class. */
    fun procAddress(name: String): Pointer? = context.procAddress(name)

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
        // BGRA as 8_8_8_8_REV is the layout drivers copy without converting (Apple's notably)
        gl.glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels)
        gl.glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun destroy() {
        deleteFramebuffer()
        context.release()
    }

    private fun deleteFramebuffer() {
        if (fbo != 0) gl.glDeleteFramebuffers(1, intArrayOf(fbo))
        if (texture != 0) gl.glDeleteTextures(1, intArrayOf(texture))
        fbo = 0
        texture = 0
    }

    companion object {
        /** The GPU's name ([renderer]), or null where there's no context to ask. */
        fun gpuName(): String? = create()?.let { context ->
            try { context.renderer } finally { context.destroy() }
        }

        /** A context made current on the calling thread, or null if this OS's API can't make one. */
        fun create(): OffscreenGl? {
            val context = runCatching {
                when {
                    Platform.isMac() -> CglContext.create()
                    Platform.isWindows() -> WglContext.create()
                    Platform.isLinux() -> EglContext.create()
                    else -> null
                }
            }.getOrNull() ?: return null
            return runCatching { OffscreenGl(context) }.getOrElse {
                context.release()
                null
            }
        }

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

/** A GL context, current on the thread that made it, and where its GL functions are. */
private interface GlContext {
    fun procAddress(name: String): Pointer?
    /** Makes it no longer current and frees it, with whatever it needed (window, surface). */
    fun release()
}

/**
 * The GL calls [OffscreenGl] makes, through the context's own function pointers: Windows only
 * exports OpenGL 1.1 directly, and on Linux which library holds GL depends on the system.
 */
@Suppress("FunctionName")
private class Gl(private val lookup: (String) -> Pointer?) {
    private val functions = HashMap<String, Function>()

    private fun function(name: String): Function = functions.getOrPut(name) {
        Function.getFunction(lookup(name) ?: error("no GL function $name"))
    }

    private fun call(name: String, vararg args: Any?) = function(name).invokeVoid(args)

    fun glGetString(name: Int): String? = function("glGetString").invokePointer(arrayOf(name))?.getString(0)
    fun glGenTextures(n: Int, ids: IntArray) = call("glGenTextures", n, ids)
    fun glDeleteTextures(n: Int, ids: IntArray) = call("glDeleteTextures", n, ids)
    fun glBindTexture(target: Int, texture: Int) = call("glBindTexture", target, texture)
    fun glTexParameteri(target: Int, name: Int, value: Int) = call("glTexParameteri", target, name, value)
    fun glTexImage2D(target: Int, level: Int, internalFormat: Int, w: Int, h: Int, border: Int, format: Int, type: Int, data: Pointer?) =
        call("glTexImage2D", target, level, internalFormat, w, h, border, format, type, data)
    fun glGenFramebuffers(n: Int, ids: IntArray) = call("glGenFramebuffers", n, ids)
    fun glDeleteFramebuffers(n: Int, ids: IntArray) = call("glDeleteFramebuffers", n, ids)
    fun glBindFramebuffer(target: Int, fbo: Int) = call("glBindFramebuffer", target, fbo)
    fun glFramebufferTexture2D(target: Int, attachment: Int, textureTarget: Int, texture: Int, level: Int) =
        call("glFramebufferTexture2D", target, attachment, textureTarget, texture, level)
    fun glCheckFramebufferStatus(target: Int): Int = function("glCheckFramebufferStatus").invokeInt(arrayOf(target))
    fun glPixelStorei(name: Int, value: Int) = call("glPixelStorei", name, value)
    fun glReadPixels(x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, pixels: Pointer) =
        call("glReadPixels", x, y, w, h, format, type, pixels)
}

// --- macOS: CGL ---------------------------------------------------------------------------------

private class CglContext(private val context: Pointer) : GlContext {
    /** OpenGL.framework exports every GL function directly. */
    override fun procAddress(name: String): Pointer? = runCatching { framework.getFunction(name) }.getOrNull()

    override fun release() {
        cgl.CGLSetCurrentContext(null)
        cgl.CGLDestroyContext(context)
    }

    companion object {
        private val framework by lazy { NativeLibrary.getInstance("OpenGL") }
        private val cgl by lazy { Native.load("OpenGL", Cgl::class.java) }

        fun create(): GlContext? {
            // 4.1 core is the newest macOS has; 3.2 core is the fallback mpv also accepts
            val context = listOf(GL4_CORE, GL3_2_CORE).firstNotNullOfOrNull(::context) ?: return null
            if (cgl.CGLSetCurrentContext(context) != 0) {
                cgl.CGLDestroyContext(context)
                return null
            }
            return CglContext(context)
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

// --- Windows: WGL -------------------------------------------------------------------------------

/**
 * WGL makes contexts for a window's device context, so this one has a window that's never shown.
 * The context is the driver's compatibility profile, which on current drivers is its newest GL.
 */
private class WglContext(private val window: Pointer, private val dc: Pointer, private val context: Pointer) : GlContext {
    override fun procAddress(name: String): Pointer? {
        // GL 1.2 and up come from wglGetProcAddress, which answers 0–3 or -1 for what it doesn't
        // have; GL 1.1 comes straight from opengl32.dll
        val address = wgl.wglGetProcAddress(name)
        if (address != null && Pointer.nativeValue(address) !in INVALID_ADDRESSES) return address
        return runCatching { opengl32.getFunction(name) }.getOrNull()
    }

    override fun release() {
        wgl.wglMakeCurrent(null, null)
        wgl.wglDeleteContext(context)
        user32.ReleaseDC(window, dc)
        user32.DestroyWindow(window)
    }

    companion object {
        private val opengl32 by lazy { NativeLibrary.getInstance("opengl32") }
        private val wgl by lazy { Native.load("opengl32", Wgl::class.java) }
        private val user32 by lazy { Native.load("user32", User32::class.java) }
        private val gdi32 by lazy { Native.load("gdi32", Gdi32::class.java) }
        private val INVALID_ADDRESSES = setOf(0L, 1L, 2L, 3L, -1L)

        fun create(): GlContext? {
            // a built-in window class, so there's none to register
            val window = user32.CreateWindowExW(0, WString("STATIC"), WString(""), WS_POPUP, 0, 0, 1, 1, null, null, null, null)
                ?: return null
            val dc = user32.GetDC(window)
            if (dc == null) {
                user32.DestroyWindow(window)
                return null
            }
            fun fail(context: Pointer? = null): GlContext? {
                context?.let(wgl::wglDeleteContext)
                user32.ReleaseDC(window, dc)
                user32.DestroyWindow(window)
                return null
            }
            val format = pixelFormatDescriptor()
            val index = gdi32.ChoosePixelFormat(dc, format)
            if (index == 0 || !gdi32.SetPixelFormat(dc, index, format)) return fail()
            val context = wgl.wglCreateContext(dc) ?: return fail()
            if (!wgl.wglMakeCurrent(dc, context)) return fail(context)
            return WglContext(window, dc, context)
        }

        /** PIXELFORMATDESCRIPTOR (40 bytes): 32-bit RGBA that OpenGL can draw to. */
        private fun pixelFormatDescriptor() = Memory(40).apply {
            clear()
            setShort(0, 40) // nSize
            setShort(2, 1) // nVersion
            setInt(4, PFD_DRAW_TO_WINDOW or PFD_SUPPORT_OPENGL or PFD_DOUBLEBUFFER) // dwFlags
            setByte(8, PFD_TYPE_RGBA) // iPixelType
            setByte(9, 32) // cColorBits
            setByte(16, 8) // cAlphaBits
        }

        private const val WS_POPUP = 0x80000000.toInt()
        private const val PFD_DOUBLEBUFFER = 0x1
        private const val PFD_DRAW_TO_WINDOW = 0x4
        private const val PFD_SUPPORT_OPENGL = 0x20
        private const val PFD_TYPE_RGBA: Byte = 0
    }
}

@Suppress("FunctionName")
private interface Wgl : Library {
    fun wglCreateContext(dc: Pointer): Pointer?
    fun wglMakeCurrent(dc: Pointer?, context: Pointer?): Boolean
    fun wglDeleteContext(context: Pointer): Boolean
    fun wglGetProcAddress(name: String): Pointer?
}

@Suppress("FunctionName")
private interface User32 : Library {
    fun CreateWindowExW(
        exStyle: Int, className: WString, windowName: WString, style: Int,
        x: Int, y: Int, width: Int, height: Int,
        parent: Pointer?, menu: Pointer?, instance: Pointer?, param: Pointer?,
    ): Pointer?
    fun GetDC(window: Pointer): Pointer?
    fun ReleaseDC(window: Pointer, dc: Pointer): Int
    fun DestroyWindow(window: Pointer): Boolean
}

@Suppress("FunctionName")
private interface Gdi32 : Library {
    fun ChoosePixelFormat(dc: Pointer, descriptor: Pointer): Int
    fun SetPixelFormat(dc: Pointer, format: Int, descriptor: Pointer): Boolean
}

// --- Linux: EGL ---------------------------------------------------------------------------------

/**
 * EGL on the desktop's display (X11 or Wayland), or with none at all through Mesa's surfaceless
 * platform. Rendering goes to [OffscreenGl]'s framebuffer, so the surface is only a 1×1
 * pbuffer, or none where the driver allows that.
 */
private class EglContext(private val display: Pointer, private val surface: Pointer?, private val context: Pointer) : GlContext {
    override fun procAddress(name: String): Pointer? =
        egl.eglGetProcAddress(name) ?: libGl?.let { runCatching { it.getFunction(name) }.getOrNull() }

    override fun release() {
        egl.eglMakeCurrent(display, null, null, null)
        surface?.let { egl.eglDestroySurface(display, it) }
        egl.eglDestroyContext(display, context)
        // the display stays initialised: eglTerminate would pull it from under other contexts on it
    }

    companion object {
        private val egl by lazy { Native.load("EGL", Egl::class.java) }
        /** Where GL lives if eglGetProcAddress doesn't hand out core functions: GLVND, then classic. */
        private val libGl by lazy {
            listOf("OpenGL", "GL").firstNotNullOfOrNull { runCatching { NativeLibrary.getInstance(it) }.getOrNull() }
        }

        fun create(): GlContext? {
            val displays = sequence {
                egl.eglGetDisplay(null)?.let { yield(it) }
                // no X11 or Wayland to talk to (or it won't do GL): Mesa's surfaceless platform
                egl.eglGetPlatformDisplay(PLATFORM_SURFACELESS_MESA, null, null)?.let { yield(it) }
            }
            return displays.firstNotNullOfOrNull(::contextOn)
        }

        private fun contextOn(display: Pointer): GlContext? {
            if (!egl.eglInitialize(display, IntByReference(), IntByReference())) return null
            if (!egl.eglBindAPI(OPENGL_API)) return null
            val config = PointerByReference()
            val count = IntByReference()
            // pbuffer configs: left out, EGL asks for window ones, which a display-less EGL has none of
            val attributes = intArrayOf(
                SURFACE_TYPE, PBUFFER_BIT, RENDERABLE_TYPE, OPENGL_BIT, RED_SIZE, 8, GREEN_SIZE, 8, BLUE_SIZE, 8, NONE,
            )
            if (!egl.eglChooseConfig(display, attributes, config, 1, count) || count.value < 1) return null
            val context = egl.eglCreateContext(display, config.value, null, intArrayOf(NONE)) ?: return null
            val surface = egl.eglCreatePbufferSurface(display, config.value, intArrayOf(WIDTH, 1, HEIGHT, 1, NONE))
            if (!egl.eglMakeCurrent(display, surface, surface, context)) {
                surface?.let { egl.eglDestroySurface(display, it) }
                egl.eglDestroyContext(display, context)
                return null
            }
            return EglContext(display, surface, context)
        }

        private const val NONE = 0x3038
        private const val RED_SIZE = 0x3024
        private const val GREEN_SIZE = 0x3023
        private const val BLUE_SIZE = 0x3022
        private const val SURFACE_TYPE = 0x3033
        private const val PBUFFER_BIT = 0x0001
        private const val RENDERABLE_TYPE = 0x3040
        private const val OPENGL_BIT = 0x0008
        private const val OPENGL_API = 0x30A2
        private const val WIDTH = 0x3057
        private const val HEIGHT = 0x3056
        private const val PLATFORM_SURFACELESS_MESA = 0x31DD
    }
}

@Suppress("FunctionName")
private interface Egl : Library {
    fun eglGetDisplay(nativeDisplay: Pointer?): Pointer?
    fun eglGetPlatformDisplay(platform: Int, nativeDisplay: Pointer?, attributes: Pointer?): Pointer?
    fun eglInitialize(display: Pointer, major: IntByReference, minor: IntByReference): Boolean
    fun eglBindAPI(api: Int): Boolean
    fun eglChooseConfig(display: Pointer, attributes: IntArray, configs: PointerByReference, size: Int, count: IntByReference): Boolean
    fun eglCreateContext(display: Pointer, config: Pointer?, share: Pointer?, attributes: IntArray): Pointer?
    fun eglCreatePbufferSurface(display: Pointer, config: Pointer?, attributes: IntArray): Pointer?
    fun eglMakeCurrent(display: Pointer, draw: Pointer?, read: Pointer?, context: Pointer?): Boolean
    fun eglDestroySurface(display: Pointer, surface: Pointer): Boolean
    fun eglDestroyContext(display: Pointer, context: Pointer): Boolean
    fun eglGetProcAddress(name: String): Pointer?
}
