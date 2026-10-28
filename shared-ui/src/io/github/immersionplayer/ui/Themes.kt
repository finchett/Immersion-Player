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
    /** Nord's Snow Storm side: its frost blue on pale grey. */
    NordLight(
        "Nord Light",
        lightColorScheme(
            primary = Color(0xFF5E81AC),
            onPrimary = Color.White,
            secondary = Color(0xFFD08770),
            tertiary = Color(0xFF8FBCBB),
            background = Color(0xFFECEFF4),
            surface = Color(0xFFECEFF4),
            surfaceVariant = Color(0xFFE2E6EE),
            onBackground = Color(0xFF2E3440),
            onSurface = Color(0xFF2E3440),
            onSurfaceVariant = Color(0xFF5B6476),
        ),
    ),
    /** Sakura by day: the blossom pink deepened, on a blush of white. */
    SakuraLight(
        "Sakura Light",
        lightColorScheme(
            primary = Color(0xFFB5456F),
            onPrimary = Color.White,
            secondary = Color(0xFFA0604A),
            tertiary = Color(0xFF3E7B5E),
            background = Color(0xFFFBF5F7),
            surface = Color(0xFFFBF5F7),
            surfaceVariant = Color(0xFFF2E6EB),
            onBackground = Color(0xFF2E2226),
            onSurface = Color(0xFF2E2226),
            onSurfaceVariant = Color(0xFF7A6670),
        ),
    ),
    /** GNOME's libadwaita: its blue accent on near-white. */
    Adwaita(
        "Adwaita",
        lightColorScheme(
            primary = Color(0xFF1C71D8),
            onPrimary = Color.White,
            secondary = Color(0xFFE66100),
            tertiary = Color(0xFF26A269),
            background = Color(0xFFFAFAFB),
            surface = Color(0xFFFAFAFB),
            surfaceVariant = Color(0xFFEBEBED),
            onBackground = Color(0xFF333338),
            onSurface = Color(0xFF333338),
            onSurfaceVariant = Color(0xFF707075),
        ),
    ),
    AdwaitaDark(
        "Adwaita Dark",
        darkColorScheme(
            primary = Color(0xFF78AEED),
            secondary = Color(0xFFFFA348),
            tertiary = Color(0xFF8FF0A4),
            background = Color(0xFF222226),
            surface = Color(0xFF222226),
            surfaceVariant = Color(0xFF2E2E32),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFFADADB1),
        ),
    ),
    /** KDE Plasma's Breeze: its cyan-blue highlight on cool grey. */
    Breeze(
        "Breeze",
        lightColorScheme(
            primary = Color(0xFF2980B9),
            onPrimary = Color.White,
            secondary = Color(0xFFF67400),
            tertiary = Color(0xFF27AE60),
            background = Color(0xFFEFF0F1),
            surface = Color(0xFFEFF0F1),
            surfaceVariant = Color(0xFFE3E5E7),
            onBackground = Color(0xFF232629),
            onSurface = Color(0xFF232629),
            onSurfaceVariant = Color(0xFF6E7880),
        ),
    ),
    BreezeDark(
        "Breeze Dark",
        darkColorScheme(
            primary = Color(0xFF3DAEE9),
            secondary = Color(0xFFF67400),
            tertiary = Color(0xFF27AE60),
            background = Color(0xFF202326),
            surface = Color(0xFF202326),
            surfaceVariant = Color(0xFF292C30),
            onBackground = Color(0xFFFCFCFC),
            onSurface = Color(0xFFFCFCFC),
            onSurfaceVariant = Color(0xFFA1A9B1),
        ),
    ),
}

/**
 * A theme's light and dark sides, chosen between by [ThemeMode]. OLED Black is dark only, so it
 * stays dark whatever the mode.
 */
enum class ThemeFamily(val label: String, val light: AppTheme?, val dark: AppTheme?) {
    Jade("Jade", AppTheme.Celadon, AppTheme.Jade),
    Sakura("Sakura", AppTheme.SakuraLight, AppTheme.Sakura),
    Nord("Nord", AppTheme.NordLight, AppTheme.Nord),
    Paper("Paper", AppTheme.Paper, AppTheme.Midnight),
    Adwaita("Adwaita", AppTheme.Adwaita, AppTheme.AdwaitaDark),
    Breeze("Breeze", AppTheme.Breeze, AppTheme.BreezeDark),
    Oled("OLED Black", null, AppTheme.Oled);

    /** The side to show for [mode], [systemDark] deciding Auto; a one-sided family shows its one side. */
    fun resolve(mode: ThemeMode, systemDark: Boolean): AppTheme {
        val wantDark = when (mode) {
            ThemeMode.Auto -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        return (if (wantDark) dark ?: light else light ?: dark)!!
    }

    fun supports(mode: ThemeMode) = when (mode) {
        ThemeMode.Auto -> light != null && dark != null
        ThemeMode.Light -> light != null
        ThemeMode.Dark -> dark != null
    }

    companion object {
        fun of(theme: AppTheme): ThemeFamily = entries.first { it.light == theme || it.dark == theme }
    }
}

/** Light, dark, or whichever the system is set to. */
enum class ThemeMode { Auto, Light, Dark }

/** The themes "System" follows, by the device's light or dark setting. */
val SystemDark = AppTheme.Jade
val SystemLight = AppTheme.Celadon
