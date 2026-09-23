package io.github.immersionplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.immersionplayer.Prefs

/** Appearance settings, observable by Compose so changes apply immediately. */
class Appearance(private val prefs: Prefs) {
    /** The chosen theme, or null to follow the phone's light/dark setting. */
    var theme: AppTheme? by mutableStateOf(
        when (val stored = prefs.theme) {
            // installs from before there was a system option keep the theme they had
            null -> AppTheme.Midnight
            SYSTEM -> null
            else -> AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.Midnight
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
