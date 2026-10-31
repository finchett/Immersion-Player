package app.mizune.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app themes, retuned for a phone: the desktop's colours, and a type scale between Material's
 * and the desktop's, a little larger than the desktop's for reading at hand's length.
 */
@Composable
fun PhoneTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val scheme = remember(theme) { theme.scheme.retuned() }
    MaterialTheme(colorScheme = scheme, typography = PhoneTypography, content = content)
}

// regular and medium weights only, as on the desktop: hierarchy comes from size and space
private val PhoneTypography = Typography().let { base ->
    fun TextStyle.sized(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    base.copy(
        headlineLarge = base.headlineLarge.sized(30, 36),
        headlineMedium = base.headlineMedium.sized(24, 30),
        headlineSmall = base.headlineSmall.sized(21, 27),
        titleLarge = base.titleLarge.sized(19, 24, FontWeight.Medium),
        titleMedium = base.titleMedium.sized(16, 22, FontWeight.Medium),
        titleSmall = base.titleSmall.sized(14, 19, FontWeight.Medium),
        bodyLarge = base.bodyLarge.sized(15, 22),
        bodyMedium = base.bodyMedium.sized(14, 20),
        bodySmall = base.bodySmall.sized(12, 17),
        labelLarge = base.labelLarge.sized(14, 19, FontWeight.Medium),
        labelMedium = base.labelMedium.sized(12, 16, FontWeight.Medium),
        labelSmall = base.labelSmall.sized(11, 14, FontWeight.Medium),
    )
}
