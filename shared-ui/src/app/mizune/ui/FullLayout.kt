package app.mizune.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * With the panel put away (Full), the part of the video where the pointer (or a tap) leaves the
 * controls alone, as fractions of the video's width and height: all but a band along the top, the
 * sides (narrowing towards the bottom), and a strip under the line that widens into the bottom
 * corners. Anywhere else brings the controls up.
 */
object QuietArea {
    private val points = listOf(
        Offset(0.12f, 0.26f),
        Offset(0.88f, 0.26f),
        Offset(0.92f, 0.895f),
        Offset(0.69f, 0.96f),
        Offset(0.31f, 0.96f),
        Offset(0.08f, 0.895f),
    )

    /** Where the line over the video rests, above the bottom edge, as a fraction of the video's height: just above the quiet area's bottom. */
    const val LINE_REST = 0.065f

    /** Whether ([x], [y]), as fractions of the video's size, is in the quiet area: counts the edges a ray to its right crosses. */
    fun contains(x: Float, y: Float): Boolean {
        var crossings = 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            if ((a.y > y) != (b.y > y) && x < a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)) crossings++
        }
        return crossings % 2 == 1
    }
}

/** A thin seek bar that thickens under the pointer, or a finger. [touchHeight] is the height it can be grabbed in. */
@Composable
fun Scrubber(
    position: Double,
    duration: Double,
    onScrub: (Double) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    touchHeight: Dp = 14.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var held by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (hovered || held) 6.dp else 3.dp, label = "scrubber")
    val played = MaterialTheme.colorScheme.primary
    fun at(x: Float, width: Int) = (x / width).coerceIn(0f, 1f) * duration
    Canvas(
        modifier.fillMaxWidth().height(touchHeight)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(duration) {
                detectTapGestures { onScrub(at(it.x, size.width)); onScrubEnd() }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        held = true
                        onScrub(at(it.x, size.width))
                    },
                    onDragEnd = {
                        held = false
                        onScrubEnd()
                    },
                    onDragCancel = {
                        held = false
                        onScrubEnd()
                    },
                ) { change, _ ->
                    change.consume()
                    onScrub(at(change.position.x, size.width))
                }
            },
    ) {
        val h = thickness.toPx()
        val y = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, y), Size(size.width, h), radius)
        drawRoundRect(played, Offset(0f, y), Size(size.width * fraction, h), radius)
    }
}
