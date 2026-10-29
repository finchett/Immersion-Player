package app.mizune.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * Windows and Linux put their own title bar above the app's header, which made two. There the
 * window has no system frame at all: the headers move it (a double-click maximises), its buttons
 * sit at the top right in the system's style, and its edges resize it. macOS keeps its frame, with
 * the content running under a transparent title bar and the traffic lights in the header.
 */
val ownsFrame = !isMac

class WindowFrame(val window: Window, val state: WindowState) {
    val maximized get() = state.placement == WindowPlacement.Maximized

    fun minimize() { state.isMinimized = true }

    fun toggleMaximized() {
        if (maximized) {
            state.placement = WindowPlacement.Floating
        } else {
            // a frameless window maximises over the taskbar unless told the screen's work area,
            // which Windows wants relative to the screen it's on
            if (isWindows) {
                val config = window.graphicsConfiguration
                val screen = config.bounds
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
                (window as? java.awt.Frame)?.maximizedBounds = Rectangle(
                    insets.left, insets.top,
                    screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom,
                )
            }
            state.placement = WindowPlacement.Maximized
        }
    }
}

/** The window's frame, where the app draws it; null on macOS. */
val LocalWindowFrame = staticCompositionLocalOf<WindowFrame?> { null }

private val WINDOWS_BUTTON_WIDTH = 46.dp

/** Room the window buttons take at the right of the top header. */
val HeaderEnd: Dp = when {
    isMac -> 0.dp
    isWindows -> WINDOWS_BUTTON_WIDTH * 3
    else -> 100.dp
}

/**
 * Lets a header move the window where nothing in it takes the press, and maximise it on a
 * double-click, where the app draws its own frame. Does nothing on macOS.
 */
fun Modifier.movesWindow(): Modifier = composed {
    val frame = LocalWindowFrame.current ?: return@composed this
    // when the last press came up without a move, for the double-click
    val lastUp = remember { LongRef(0) }
    pointerInput(frame) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            if (down.uptimeMillis - lastUp.value < 400) {
                frame.toggleMaximized()
                lastUp.value = 0
                return@awaitEachGesture
            }
            val startMouse = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            val startWindow = frame.window.location
            var moved = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!moved) lastUp.value = change.uptimeMillis
                    break
                }
                val mouse = MouseInfo.getPointerInfo()?.location ?: break
                val dx = mouse.x - startMouse.x
                val dy = mouse.y - startMouse.y
                if (!moved && dx * dx + dy * dy > 9 && !frame.maximized) moved = true
                if (moved) {
                    frame.window.setLocation(startWindow.x + dx, startWindow.y + dy)
                    change.consume()
                }
            }
        }
    }
}

private class LongRef(var value: Long)

/** Minimise, maximise and close, at the top right; [onDark] when they sit over the video. */
@Composable
fun WindowButtons(frame: WindowFrame, onClose: () -> Unit, onDark: Boolean, modifier: Modifier = Modifier) {
    val glyph = if (onDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    val hover = if (onDark) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    if (isWindows) {
        // Windows 11's own: flat, the full header's height, close turning red
        Row(modifier.height(HeaderHeight)) {
            WindowsButton(glyph, hover, frame::minimize) { color -> line(color, 0.5f) }
            WindowsButton(glyph, hover, frame::toggleMaximized) { color -> if (frame.maximized) restore(color) else square(color) }
            WindowsButton(glyph, Color(0xFFC42B1C), onClose, closeHover = true) { color -> cross(color) }
        }
    } else {
        // GNOME's: small round buttons with a soft fill, a little apart
        Row(
            modifier.height(HeaderHeight).padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(glyph, hover, frame::minimize) { color -> line(color, 0.62f) }
            RoundButton(glyph, hover, frame::toggleMaximized) { color -> if (frame.maximized) restore(color) else square(color) }
            RoundButton(glyph, hover, onClose) { color -> cross(color) }
        }
    }
}

private typealias Glyph = androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit

@Composable
private fun WindowsButton(glyph: Color, hover: Color, onClick: () -> Unit, closeHover: Boolean = false, draw: Glyph) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.width(WINDOWS_BUTTON_WIDTH).fillMaxHeight()
            .background(if (hovered) hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { draw(if (hovered && closeHover) Color.White else glyph) }
    }
}

@Composable
private fun RoundButton(glyph: Color, hover: Color, onClick: () -> Unit, draw: Glyph) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (hovered) hover.copy(alpha = hover.alpha * 2) else hover)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(8.dp)) { draw(glyph) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.line(color: Color, y: Float) =
    drawLine(color, Offset(0f, size.height * y), Offset(size.width, size.height * y), 1.dp.toPx())

private fun androidx.compose.ui.graphics.drawscope.DrawScope.square(color: Color) {
    val stroke = 1.dp.toPx()
    drawRect(color, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
}

/** Two squares, the back one peeking out: what the window goes back to. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.restore(color: Color) {
    val stroke = 1.dp.toPx()
    val inset = size.width * 0.22f
    drawRect(color, Offset(stroke / 2, inset), Size(size.width - inset - stroke, size.height - inset - stroke / 2), style = Stroke(stroke))
    drawLine(color, Offset(inset, inset), Offset(inset, stroke / 2), stroke)
    drawLine(color, Offset(inset, stroke / 2), Offset(size.width - stroke / 2, stroke / 2), stroke)
    drawLine(color, Offset(size.width - stroke / 2, stroke / 2), Offset(size.width - stroke / 2, size.height - inset), stroke)
    drawLine(color, Offset(size.width - stroke / 2, size.height - inset), Offset(size.width - inset, size.height - inset), stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cross(color: Color) {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke)
}

/** Thin strips along the edges and corners that resize a floating window. */
@Composable
fun BoxScope.ResizeEdges(frame: WindowFrame) {
    if (frame.state.placement != WindowPlacement.Floating) return
    val edge = 5.dp
    val corner = 12.dp
    @Composable
    fun zone(alignment: Alignment, modifier: Modifier, cursor: Int, left: Boolean = false, top: Boolean = false, right: Boolean = false, bottom: Boolean = false) {
        Box(
            modifier.align(alignment)
                .pointerHoverIcon(PointerIcon(Cursor(cursor)))
                .pointerInput(frame) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startMouse = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                        val start = frame.window.bounds
                        val min = frame.window.minimumSize
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val mouse = MouseInfo.getPointerInfo()?.location ?: break
                            val dx = mouse.x - startMouse.x
                            val dy = mouse.y - startMouse.y
                            var x = start.x
                            var y = start.y
                            var w = start.width
                            var h = start.height
                            if (right) w = maxOf(min.width, start.width + dx)
                            if (bottom) h = maxOf(min.height, start.height + dy)
                            if (left) { w = maxOf(min.width, start.width - dx); x = start.x + start.width - w }
                            if (top) { h = maxOf(min.height, start.height - dy); y = start.y + start.height - h }
                            frame.window.setBounds(x, y, w, h)
                            change.consume()
                        }
                    }
                },
        )
    }
    zone(Alignment.CenterStart, Modifier.width(edge).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, left = true)
    zone(Alignment.CenterEnd, Modifier.width(edge).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, right = true)
    zone(Alignment.TopCenter, Modifier.height(edge).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, top = true)
    zone(Alignment.BottomCenter, Modifier.height(edge).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, bottom = true)
    zone(Alignment.TopStart, Modifier.size(corner), Cursor.NW_RESIZE_CURSOR, left = true, top = true)
    zone(Alignment.TopEnd, Modifier.size(corner), Cursor.NE_RESIZE_CURSOR, right = true, top = true)
    zone(Alignment.BottomStart, Modifier.size(corner), Cursor.SW_RESIZE_CURSOR, left = true, bottom = true)
    zone(Alignment.BottomEnd, Modifier.size(corner), Cursor.SE_RESIZE_CURSOR, right = true, bottom = true)
}
