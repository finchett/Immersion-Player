package io.github.immersionplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.immersionplayer.Prefs

/** Appearance settings, observable by Compose so changes apply immediately. */
class Appearance(private val prefs: Prefs) {
    var theme by mutableStateOf(AppTheme.entries.firstOrNull { it.name == prefs.theme } ?: AppTheme.Midnight)
        private set
    var roundedCorners by mutableStateOf(prefs.roundedCorners)
        private set

    fun updateTheme(value: AppTheme) {
        theme = value
        prefs.theme = value.name
    }

    fun updateRoundedCorners(value: Boolean) {
        roundedCorners = value
        prefs.roundedCorners = value
    }
}
