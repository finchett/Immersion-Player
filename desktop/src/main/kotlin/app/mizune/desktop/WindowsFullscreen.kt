package app.mizune.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Window

/**
 * Full screen on Windows, the way video players do it: the caption and frame come off and the
 * window covers its monitor, taskbar included. Compose's own full screen there only maximises the
 * window under its title bar. Leaving puts the frame and the window's old place back.
 */
object WindowsFullscreen {
    @Structure.FieldOrder("left", "top", "right", "bottom")
    class Rect : Structure() {
        @JvmField var left = 0
        @JvmField var top = 0
        @JvmField var right = 0
        @JvmField var bottom = 0
    }

    @Structure.FieldOrder("cbSize", "rcMonitor", "rcWork", "dwFlags")
    class MonitorInfo : Structure() {
        @JvmField var cbSize = size()
        @JvmField var rcMonitor = Rect()
        @JvmField var rcWork = Rect()
        @JvmField var dwFlags = 0
    }

    private interface User32 : Library {
        fun GetWindowLongW(hwnd: Pointer, index: Int): Int
        fun SetWindowLongW(hwnd: Pointer, index: Int, value: Int): Int
        fun GetWindowRect(hwnd: Pointer, rect: Rect): Boolean
        fun MonitorFromWindow(hwnd: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: MonitorInfo): Boolean
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    }

    private val user32 by lazy { Native.load("user32", User32::class.java) }

    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    /** The style and place to go back to, while full screen. */
    private var saved: Pair<Int, Rect>? = null

    fun set(window: Window, on: Boolean) {
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            val flags = SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED
            if (on && saved == null) {
                val style = user32.GetWindowLongW(hwnd, GWL_STYLE)
                val place = Rect().also { user32.GetWindowRect(hwnd, it) }
                val monitor = user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return
                val info = MonitorInfo().also { if (!user32.GetMonitorInfoW(monitor, it)) return }
                saved = style to place
                user32.SetWindowLongW(hwnd, GWL_STYLE, style and (WS_CAPTION or WS_THICKFRAME).inv())
                with(info.rcMonitor) { user32.SetWindowPos(hwnd, null, left, top, right - left, bottom - top, flags) }
            } else if (!on) {
                val (style, place) = saved ?: return
                saved = null
                user32.SetWindowLongW(hwnd, GWL_STYLE, style)
                with(place) { user32.SetWindowPos(hwnd, null, left, top, right - left, bottom - top, flags) }
            }
        }
    }
}
