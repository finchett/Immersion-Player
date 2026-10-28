package io.github.immersionplayer.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * The native Windows title bar in the app's colours. Windows 11 draws the caption, its text and
 * the window border in whatever colours the app asks for, keeping its own buttons, snapping and
 * shadow; dark mode turns those buttons' glyphs light. Windows 10 ignores the colours.
 */
object WindowsTitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm by lazy { Native.load("dwmapi", Dwm::class.java) }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    fun apply(window: Window, caption: Color, text: Color) {
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            set(hwnd, USE_IMMERSIVE_DARK_MODE, if (caption.luminance() < 0.5f) 1 else 0)
            set(hwnd, CAPTION_COLOR, caption.colorRef())
            set(hwnd, BORDER_COLOR, caption.colorRef())
            set(hwnd, TEXT_COLOR, text.colorRef())
        }
    }

    private fun set(hwnd: Pointer, attribute: Int, value: Int) {
        dwm.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)
    }

    /** A Win32 COLORREF: 0x00BBGGRR. */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        return (argb and 0xFF shl 16) or (argb and 0xFF00) or (argb shr 16 and 0xFF)
    }
}
