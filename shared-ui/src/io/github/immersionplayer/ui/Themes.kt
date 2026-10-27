package io.github.immersionplayer.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppTheme(val label: String, val scheme: ColorScheme) {
    /** The app's own colours: a jade accent on warm charcoal, like ink and jade. */
    Jade(
        "Jade",
        darkColorScheme(
            primary = Color(0xFFB9DFC8),
            onPrimary = Color(0xFF161513),
            secondary = Color(0xFFE0C3A3),
            tertiary = Color(0xFF8FCFB4),
            background = Color(0xFF161513),
            surface = Color(0xFF161513),
            surfaceVariant = Color(0xFF23211E),
            onBackground = Color(0xFFE6E2DA),
            onSurface = Color(0xFFE6E2DA),
            onSurfaceVariant = Color(0xFFADA79D),
        ),
    ),
    /** Jade's light side: the same accent on warm paper. */
    Celadon(
        "Celadon",
        lightColorScheme(
            primary = Color(0xFF245D4E),
            onPrimary = Color(0xFFF5F3EE),
            secondary = Color(0xFF8A4F35),
            tertiary = Color(0xFF2F7A62),
            background = Color(0xFFF5F3EE),
            surface = Color(0xFFF5F3EE),
            surfaceVariant = Color(0xFFEAE6DE),
            onBackground = Color(0xFF2A2722),
            onSurface = Color(0xFF2A2722),
            onSurfaceVariant = Color(0xFF6A645A),
        ),
    ),
    Midnight(
        "Midnight",
        darkColorScheme(
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFFF2B8B5),
            tertiary = Color(0xFFA8DAB5),
            background = Color(0xFF111316),
            surface = Color(0xFF111316),
            surfaceVariant = Color(0xFF1E2126),
        ),
    ),
    Oled(
        "OLED Black",
        darkColorScheme(
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFFF2B8B5),
            tertiary = Color(0xFFA8DAB5),
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
        ),
    ),
    Nord(
        "Nord",
        darkColorScheme(
            primary = Color(0xFF88C0D0),
            secondary = Color(0xFFD08770),
            tertiary = Color(0xFFA3BE8C),
            background = Color(0xFF2E3440),
            surface = Color(0xFF2E3440),
            surfaceVariant = Color(0xFF3B4252),
            onSurface = Color(0xFFECEFF4),
            onSurfaceVariant = Color(0xFFC3CAD6),
            onBackground = Color(0xFFECEFF4),
        ),
    ),
    Sakura(
        "Sakura",
        darkColorScheme(
            primary = Color(0xFFF4A6C0),
            secondary = Color(0xFFE8B4A0),
            tertiary = Color(0xFFB8E0C8),
            background = Color(0xFF1A1417),
            surface = Color(0xFF1A1417),
            surfaceVariant = Color(0xFF2A2025),
            onSurfaceVariant = Color(0xFFD6C3CB),
        ),
    ),
    Paper(
        "Paper",
        lightColorScheme(
            primary = Color(0xFF3D6BB3),
            secondary = Color(0xFFA0453F),
            tertiary = Color(0xFF3E7B55),
            background = Color(0xFFF6F1E7),
            surface = Color(0xFFF6F1E7),
            surfaceVariant = Color(0xFFE9E1D1),
            onSurface = Color(0xFF2B2620),
            onSurfaceVariant = Color(0xFF6B6257),
            onBackground = Color(0xFF2B2620),
        ),
    ),
}

/** The themes "System" follows, by the device's light or dark setting. */
val SystemDark = AppTheme.Jade
val SystemLight = AppTheme.Celadon
