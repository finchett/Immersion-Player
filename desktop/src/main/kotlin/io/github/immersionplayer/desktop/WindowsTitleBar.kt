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
 * The window's edge in the app's colours. The app draws its own title bar ([WindowFrame]), so
 * what Windows 11 still draws is the border, which takes the background's colour, and the
 * corners, rounded while the window floats; dark mode matches the system menus to dark themes.
 * Windows 10 ignores all of it.
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
    private const val CORNER_PREFERENCE = 33
    private const val CORNERS_SQUARE = 1
    private const val CORNERS_ROUND = 2

    fun apply(window: Window, caption: Color, text: Color, rounded: Boolean) {
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            set(hwnd, USE_IMMERSIVE_DARK_MODE, if (caption.luminance() < 0.5f) 1 else 0)
            set(hwnd, CAPTION_COLOR, caption.colorRef())
            set(hwnd, BORDER_COLOR, caption.colorRef())
            set(hwnd, TEXT_COLOR, text.colorRef())
            set(hwnd, CORNER_PREFERENCE, if (rounded) CORNERS_ROUND else CORNERS_SQUARE)
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
