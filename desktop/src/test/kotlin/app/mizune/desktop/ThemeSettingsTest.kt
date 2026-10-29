package app.mizune.desktop

import app.mizune.ui.AppTheme
import app.mizune.ui.ThemeFamily
import app.mizune.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.prefs.Preferences

class ThemeSettingsTest {
    private val node = "app/mizune-test-theme"

    private fun settingsWithOldTheme(theme: String?): Settings {
        Preferences.userRoot().node(node).removeNode()
        if (theme != null) Preferences.userRoot().node(node).put("theme", theme)
        return Settings(node)
    }

    @Test
    fun aThemeFromBeforeFamiliesCarriesOver() {
        try {
            settingsWithOldTheme(null).let {
                assertEquals(ThemeFamily.Jade to ThemeMode.Auto, it.themeFamily to it.themeMode)
            }
            settingsWithOldTheme("Celadon").let {
                assertEquals(ThemeFamily.Jade to ThemeMode.Light, it.themeFamily to it.themeMode)
            }
            settingsWithOldTheme("Midnight").let {
                assertEquals(ThemeFamily.Paper to ThemeMode.Dark, it.themeFamily to it.themeMode)
            }
            // and the new choice wins once made
            settingsWithOldTheme("Nord").apply {
                updateThemeFamily(ThemeFamily.Breeze)
                updateThemeMode(ThemeMode.Auto)
            }
            Settings(node).let { assertEquals(ThemeFamily.Breeze to ThemeMode.Auto, it.themeFamily to it.themeMode) }
        } finally {
            Preferences.userRoot().node(node).removeNode()
        }
    }

    @Test
    fun aFamilyResolvesToItsSide() {
        assertEquals(AppTheme.AdwaitaDark, ThemeFamily.Adwaita.resolve(ThemeMode.Auto, systemDark = true))
        assertEquals(AppTheme.Adwaita, ThemeFamily.Adwaita.resolve(ThemeMode.Auto, systemDark = false))
        assertEquals(AppTheme.Adwaita, ThemeFamily.Adwaita.resolve(ThemeMode.Light, systemDark = true))
        // dark only, whatever's asked
        assertEquals(AppTheme.Oled, ThemeFamily.Oled.resolve(ThemeMode.Light, systemDark = false))
    }
}
