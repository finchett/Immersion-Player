package io.github.immersionplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A cog: a ring with eight teeth, drawn rather than shipped as an icon font. */
@Composable
fun Cog(color: Color, diameter: Dp = 14.dp) {
    Canvas(Modifier.size(diameter)) {
        val r = size.minDimension / 2
        drawCircle(color, radius = r * 0.48f, style = Stroke(width = r * 0.24f))
        repeat(8) { i ->
            rotate(i * 45f) {
                // each tooth runs into the ring, so the cog reads as one shape, not a sun
                drawRoundRect(
                    color,
                    topLeft = Offset(center.x - r * 0.11f, center.y - r),
                    size = Size(r * 0.22f, r * 0.48f),
                    cornerRadius = CornerRadius(r * 0.07f),
                )
            }
        }
    }
}
