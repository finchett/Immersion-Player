package io.github.immersionplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.ui.AppTheme

val isMac = System.getProperty("os.name").lowercase().contains("mac")

/** Whether the macOS traffic lights are drawn over the content (not in full screen). */
val LocalTrafficLights = staticCompositionLocalOf { isMac }

/**
 * Corner geometry, all concentric with the window corner (radius R): each layer steps in by the
 * same amount, from the window edge to the video card, the pill, and the traffic lights.
 * On macOS R is the real window radius; elsewhere there are no lights, so plain values.
 */
class Corners(windowRadius: Double) {
    /** One step in, shared by every layer: three steps span the window corner to a light's edge. */
    val step: Dp = if (isMac) ((windowRadius - MacTrafficLights.LIGHT_RADIUS) / 3).dp else 6.dp
    val cardGap: Dp = step
    val cardRadius: Dp = if (isMac) windowRadius.dp - step else 12.dp
    /** Centre of the corner curve, where the first light sits. */
    val lightCenter: Dp = windowRadius.dp
    val pillRadius: Dp = MacTrafficLights.LIGHT_RADIUS.dp + step
}

val LocalCorners = staticCompositionLocalOf { Corners(16.0) }

/** Where a header on the title-bar line starts: clear of the traffic lights when they're shown. */
val HeaderStart: Dp
    @Composable get() {
        if (!LocalTrafficLights.current) return 12.dp
        val c = LocalCorners.current
        return c.lightCenter + (MacTrafficLights.LIGHT_SPACING * 2).dp + c.pillRadius + 12.dp
    }

/** Height of a header row whose middle lines up with the traffic lights. */
val HeaderHeight: Dp
    @Composable get() = if (LocalTrafficLights.current) LocalCorners.current.lightCenter * 2 else 40.dp

/** A translucent pill behind the traffic lights, concentric with the window corner; fades with them. */
@Composable
fun TrafficLightsPill(visible: Boolean, modifier: Modifier = Modifier) {
    if (!LocalTrafficLights.current) return
    val c = LocalCorners.current
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "pill")
    Box(
        modifier.padding(start = c.lightCenter - c.pillRadius, top = c.lightCenter - c.pillRadius)
            .size(width = (MacTrafficLights.LIGHT_SPACING * 2).dp + c.pillRadius * 2, height = c.pillRadius * 2)
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(c.pillRadius)),
    )
}

/**
 * The app themes, retuned for a desktop: a denser type scale sized for reading at arm's length
 * with a mouse, no 48 dp touch targets, and the colours Material would otherwise fill with its
 * default purples derived from each theme instead.
 */
@Composable
fun DesktopTheme(choice: AppTheme?, content: @Composable () -> Unit) {
    val theme = choice ?: if (isSystemInDarkTheme()) AppTheme.Midnight else AppTheme.Paper
    val scheme = remember(theme) { theme.scheme.forDesktop() }
    MaterialTheme(colorScheme = scheme, typography = DesktopTypography) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp, content = content)
    }
}

private fun ColorScheme.forDesktop(): ColorScheme = copy(
    onPrimary = background,
    primaryContainer = primary.copy(alpha = 0.22f).compositeOver(surface),
    onPrimaryContainer = onSurface,
    secondaryContainer = surfaceVariant,
    onSecondaryContainer = onSurface,
    surfaceContainerLowest = background,
    surfaceContainerLow = surface,
    surfaceContainer = surfaceVariant,
    surfaceContainerHigh = surfaceVariant,
    surfaceContainerHighest = surfaceVariant,
    outline = onSurfaceVariant.copy(alpha = 0.5f),
    outlineVariant = onSurfaceVariant.copy(alpha = 0.18f),
)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
    )
}

private val DesktopTypography = Typography().let { base ->
    fun TextStyle.sized(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    base.copy(
        headlineMedium = base.headlineMedium.sized(24, 30, FontWeight.Medium),
        headlineSmall = base.headlineSmall.sized(20, 26),
        titleLarge = base.titleLarge.sized(17, 22, FontWeight.SemiBold),
        titleMedium = base.titleMedium.sized(14, 20, FontWeight.SemiBold),
        titleSmall = base.titleSmall.sized(13, 18, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sized(14, 21),
        bodyMedium = base.bodyMedium.sized(13, 19),
        bodySmall = base.bodySmall.sized(12, 17),
        labelLarge = base.labelLarge.sized(13, 18, FontWeight.Medium),
        labelMedium = base.labelMedium.sized(12, 16, FontWeight.Medium),
        labelSmall = base.labelSmall.sized(11, 14, FontWeight.Medium),
    )
}

/** A text action: no button chrome, a tint on hover. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text,
        style = style,
        color = when {
            !enabled -> color.copy(alpha = 0.35f)
            hovered -> MaterialTheme.colorScheme.onSurface
            else -> color
        },
        maxLines = 1,
        modifier = modifier
            .background(
                if (hovered && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
