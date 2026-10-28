package io.github.immersionplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Back a line: a bar, and a triangle pointing at it. [forward] mirrors it. */
@Composable
fun LineSkipIcon(color: Color, forward: Boolean, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        scale(if (forward) -1f else 1f, 1f) {
            val w = size.width
            val h = size.height
            drawRoundRect(color, Offset(w * 0.16f, h * 0.18f), Size(w * 0.14f, h * 0.64f), CornerRadius(w * 0.04f))
            val triangle = Path().apply {
                moveTo(w * 0.36f, h * 0.5f)
                lineTo(w * 0.84f, h * 0.18f)
                lineTo(w * 0.84f, h * 0.82f)
                close()
            }
            drawPath(triangle, color)
        }
    }
}

/**
 * Play: a triangle, set a little right of centre so it looks centred (its weight sits towards
 * its flat side).
 */
@Composable
fun PlayIcon(color: Color, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(w * 0.27f, h * 0.16f)
            lineTo(w * 0.87f, h * 0.5f)
            lineTo(w * 0.27f, h * 0.84f)
            close()
        }
        drawPath(triangle, color)
    }
}

/** Pause: two bars. */
@Composable
fun PauseIcon(color: Color, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(w * 0.05f)
        drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.18f, h * 0.64f), radius)
        drawRoundRect(color, Offset(w * 0.58f, h * 0.18f), Size(w * 0.18f, h * 0.64f), radius)
    }
}

/** Replay the line: an arrow most of the way round anticlockwise, its head at the top. */
@Composable
fun ReplayIcon(color: Color, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        // drawn clockwise from two o'clock round to twelve, then mirrored
        scale(-1f, 1f) {
            val stroke = size.minDimension * 0.12f
            val inset = size.minDimension * 0.2f
            drawArc(
                color,
                startAngle = -30f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // the head at twelve o'clock, pointing on round the circle
            val x = size.width / 2
            val a = size.minDimension * 0.2f
            val head = Path().apply {
                moveTo(x + a, inset)
                lineTo(x - a * 0.4f, inset - a)
                lineTo(x - a * 0.4f, inset + a)
                close()
            }
            drawPath(head, color)
        }
    }
}

/** Stop at the line's end: the line, then a pause. */
@Composable
fun LineStopIcon(color: Color, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(w * 0.05f)
        drawRoundRect(color, Offset(w * 0.04f, h * 0.44f), Size(w * 0.4f, h * 0.12f), radius)
        drawRoundRect(color, Offset(w * 0.54f, h * 0.2f), Size(w * 0.14f, h * 0.6f), radius)
        drawRoundRect(color, Offset(w * 0.8f, h * 0.2f), Size(w * 0.14f, h * 0.6f), radius)
    }
}

/** Full screen: four corners bent outwards, or bent inwards to leave it while [full]. */
@Composable
fun FullscreenIcon(color: Color, full: Boolean, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        val d = size.minDimension
        val stroke = Stroke(d * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val near = d * 0.12f
        val far = d - near
        val arm = d * 0.28f
        for (x in listOf(near, far)) for (y in listOf(near, far)) {
            // the arms run along the edges from each corner; leaving, they meet inside instead
            val dx = if (x == near) arm else -arm
            val dy = if (y == near) arm else -arm
            val path = Path().apply {
                moveTo(x + dx, y)
                if (full) lineTo(x + dx, y + dy) else lineTo(x, y)
                lineTo(x, y + dy)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/** The study panel: a window with its right-hand column, filled while the panel [shown]. */
@Composable
fun PanelIcon(color: Color, shown: Boolean, diameter: Dp = 16.dp) {
    Canvas(Modifier.size(diameter)) {
        val stroke = size.minDimension * 0.1f
        val w = size.width
        val h = size.height
        val top = h * 0.16f
        val height = h * 0.68f
        val radius = CornerRadius(w * 0.12f)
        drawRoundRect(color, Offset(w * 0.06f, top), Size(w * 0.88f, height), radius, style = Stroke(stroke))
        val column = w * 0.62f
        if (shown) {
            drawRoundRect(color, Offset(column, top), Size(w * 0.94f - column, height), radius)
        } else {
            drawLine(color, Offset(column, top), Offset(column, top + height), stroke)
        }
    }
}
