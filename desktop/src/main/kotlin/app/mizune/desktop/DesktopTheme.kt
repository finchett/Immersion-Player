package app.mizune.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.ui.AppTheme
import app.mizune.ui.Cog
import app.mizune.ui.SystemDark
import app.mizune.ui.SystemLight
import app.mizune.ui.ThemeFamily
import app.mizune.ui.ThemeMode

val isMac = System.getProperty("os.name").lowercase().contains("mac")
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val isLinux = System.getProperty("os.name").lowercase().contains("linux")

/** Whether the macOS traffic lights are drawn over the content (not in full screen). */
val LocalTrafficLights = staticCompositionLocalOf { isMac }

/**
 * Corner geometry, concentric with the window corner (radius R): the video card steps in from
 * the window edge and rounds about the same centre. On macOS R is the real window radius;
 * elsewhere, plain values.
 */
class Corners(windowRadius: Double) {
    // only a window with rounded corners has anything to be concentric with; full screen
    // (radius 0) and other systems get plain values
    private val concentric = isMac && windowRadius > MacTrafficLights.LIGHT_RADIUS
    /** One step in: a third of the way from the window corner to a light's radius. */
    private val step: Dp = if (concentric) ((windowRadius - MacTrafficLights.LIGHT_RADIUS) / 3).dp else 6.dp
    val cardGap: Dp = step
    val cardRadius: Dp = if (concentric) windowRadius.dp - step else 12.dp
}

val LocalCorners = staticCompositionLocalOf { Corners(16.0) }

/**
 * Height of every header, the player panel's too. The traffic lights are centred in it, as far in
 * from the left as from the top, the way a Mac toolbar holds them, and stay there on every screen.
 */
val HeaderHeight = 40.dp

/** Where a header on the title-bar line starts: clear of the traffic lights when they're shown. */
val HeaderStart: Dp
    @Composable get() {
        if (!LocalTrafficLights.current) return 12.dp
        return HeaderHeight / 2 + (MacTrafficLights.LIGHT_SPACING * 2 + MacTrafficLights.LIGHT_RADIUS).dp + 16.dp
    }

/**
 * The app themes, retuned for a desktop: a denser type scale sized for reading at arm's length
 * with a mouse, no 48 dp touch targets, and the colours Material would otherwise fill with its
 * default purples derived from each theme instead.
 */
@Composable
fun DesktopTheme(family: ThemeFamily, mode: ThemeMode, content: @Composable () -> Unit) =
    DesktopTheme(family.resolve(mode, isSystemInDarkTheme()), content)

@Composable
fun DesktopTheme(choice: AppTheme?, content: @Composable () -> Unit) {
    val theme = choice ?: if (isSystemInDarkTheme()) SystemDark else SystemLight
    val scheme = remember(theme) { theme.scheme.forDesktop() }
    MaterialTheme(colorScheme = scheme, typography = DesktopTypography) {
        // default text colour for everything, so no screen depends on a Surface to set it
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 0.dp,
            LocalContentColor provides scheme.onBackground,
            content = content,
        )
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

// regular and medium weights only: hierarchy comes from size and space
private val DesktopTypography = Typography().let { base ->
    fun TextStyle.sized(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    base.copy(
        headlineLarge = base.headlineLarge.sized(28, 34),
        headlineMedium = base.headlineMedium.sized(24, 30),
        headlineSmall = base.headlineSmall.sized(20, 26),
        titleLarge = base.titleLarge.sized(17, 22, FontWeight.Medium),
        titleMedium = base.titleMedium.sized(14, 20, FontWeight.Medium),
        titleSmall = base.titleSmall.sized(13, 18, FontWeight.Medium),
        bodyLarge = base.bodyLarge.sized(14, 21),
        bodyMedium = base.bodyMedium.sized(13, 19),
        bodySmall = base.bodySmall.sized(12, 17),
        labelLarge = base.labelLarge.sized(13, 18, FontWeight.Medium),
        labelMedium = base.labelMedium.sized(12, 16, FontWeight.Medium),
        labelSmall = base.labelSmall.sized(11, 14, FontWeight.Medium),
    )
}

/** A quiet label over a group: small capitals, spaced out, with an optional note at the far end. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, note: String? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text.uppercase(),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Medium,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted.copy(alpha = 0.8f)) }
    }
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
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text,
        style = style,
        color = when {
            !enabled -> color.copy(alpha = 0.35f)
            hovered -> hoverColor
            else -> color
        },
        maxLines = 1,
        modifier = modifier
            .background(
                if (hovered && enabled) hoverColor.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** An action drawn as an icon rather than a word, with the same hover tint as [TextAction]. */
@Composable
fun IconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) hoverColor.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(5.dp),
    ) {
        icon(if (hovered) hoverColor else color)
    }
}

/** The app icon in use (light or dark, as the Dock's is set), for the middle of the header. */
val LocalAppIcon = staticCompositionLocalOf { "icon.png" }

/** How wide the start of a header may run and stay clear of the app icon in the middle. */
val LocalHeaderLead = staticCompositionLocalOf { Dp.Infinity }

// the icon carries its own margin, so 28 dp draws a 22 dp tile
private val HeaderIconSize = 28.dp

/**
 * A header bar that stays put, a hairline, and the body below it. A screen with a sidebar passes
 * it as [leading]: it runs the full height, the traffic lights sit over its top, and the header
 * and its hairline start where it ends. The app's icon sits in the middle of the window on the
 * header's line, the way a Mac window centres its title, and stays there from screen to screen.
 */
@Composable
fun Chrome(header: @Composable () -> Unit, body: @Composable () -> Unit, leading: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val windowWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            leading?.invoke()
            Column(Modifier.fillMaxSize()) {
                val end = 12.dp + HeaderEnd
                Box(
                    Modifier.fillMaxWidth().height(HeaderHeight).movesWindow()
                        .padding(start = if (leading == null) HeaderStart else 32.dp, end = end),
                ) {
                    BoxWithConstraints {
                        // the header ends [end] short of the window's edge, so the window's middle
                        // is this far in from where the header's content starts
                        val middle = maxWidth + end - windowWidth / 2
                        val lead = (middle - HeaderIconSize / 2 - 8.dp).coerceAtLeast(0.dp)
                        CompositionLocalProvider(LocalHeaderLead provides lead) { header() }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.fillMaxSize()) { body() }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).height(HeaderHeight), contentAlignment = Alignment.Center) {
            Image(painterResource(LocalAppIcon.current), contentDescription = null, modifier = Modifier.size(HeaderIconSize))
        }
    }
}
