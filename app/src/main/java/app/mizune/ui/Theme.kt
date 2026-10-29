package app.mizune.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mizune.Prefs

/** Appearance settings, observable by Compose so changes apply immediately. */
class Appearance(private val prefs: Prefs) {
    /** The chosen theme, or null to follow the phone's light/dark setting. */
    var theme: AppTheme? by mutableStateOf(
        when (val stored = prefs.theme) {
            // nothing chosen follows the phone, in the app's own Jade and Celadon
            null, SYSTEM -> null
            else -> AppTheme.entries.firstOrNull { it.name == stored }
        },
    )
        private set
    var roundedCorners by mutableStateOf(prefs.roundedCorners)
        private set

    fun updateTheme(value: AppTheme?) {
        theme = value
        prefs.theme = value?.name ?: SYSTEM
    }

    fun updateRoundedCorners(value: Boolean) {
        roundedCorners = value
        prefs.roundedCorners = value
    }

    private companion object {
        const val SYSTEM = "system"
    }
}
