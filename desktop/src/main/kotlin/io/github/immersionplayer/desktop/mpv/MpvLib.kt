package io.github.immersionplayer.desktop.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer

/** The parts of libmpv's client and render APIs the player uses. */
@Suppress("FunctionName")
interface MpvLib : Library {
    fun mpv_client_api_version(): Long
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_command_async(handle: Pointer, replyUserdata: Long, args: Array<String?>): Int
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(handle: Pointer)
    fun mpv_error_string(error: Int): String

    fun mpv_render_context_create(result: Array<Pointer?>, handle: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(context: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(context: Pointer): Long
    fun mpv_render_context_render(context: Pointer, params: Pointer): Int
    fun mpv_render_context_free(context: Pointer)

    fun interface UpdateCallback : Callback {
        fun invoke(data: Pointer?)
    }

    companion object {
        const val FORMAT_STRING = 1
        const val FORMAT_FLAG = 3
        const val FORMAT_DOUBLE = 5

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_FILE_LOADED = 8
        const val EVENT_END_FILE = 7
        const val EVENT_PROPERTY_CHANGE = 22

        const val RENDER_PARAM_INVALID = 0
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20

        const val RENDER_UPDATE_FRAME = 1L

        val INSTANCE: MpvLib by lazy {
            useCNumericLocale()
            Native.load(bundled() ?: if (Platform.isWindows()) "libmpv-2" else "mpv", MpvLib::class.java)
        }

        /** The libmpv packaged with the app, if this is a packaged build; else the system's. */
        private fun bundled(): String? {
            val dir = System.getProperty("compose.application.resources.dir") ?: return null
            return listOf("libmpv.2.dylib", "libmpv-2.dll", "libmpv.so.2")
                .map { java.io.File(dir, it) }
                .firstOrNull { it.isFile }?.path
        }

        /** mpv refuses to start unless LC_NUMERIC is "C" (it parses floats with the C library). */
        private fun useCNumericLocale() {
            if (Platform.isWindows()) return
            val lcNumeric = if (Platform.isMac()) 4 else 1
            runCatching {
                Native.load("c", LibC::class.java).setlocale(lcNumeric, "C")
            }
        }
    }
}

internal interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}
