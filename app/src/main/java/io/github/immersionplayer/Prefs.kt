package io.github.immersionplayer

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    var libraryTreeUri: String?
        get() = prefs.getString("library_tree_uri", null)
        set(value) = prefs.edit { putString("library_tree_uri", value) }

    /** Pause when the current line ends (mpvacious "play up to next"). */
    var autoPause: Boolean
        get() = prefs.getBoolean("auto_pause", false)
        set(value) = prefs.edit { putBoolean("auto_pause", value) }

    /** Copy each new line to the clipboard, for use with an external dictionary. */
    var copyLines: Boolean
        get() = prefs.getBoolean("copy_lines", false)
        set(value) = prefs.edit { putBoolean("copy_lines", value) }

    var showTranslation: Boolean
        get() = prefs.getBoolean("show_translation", false)
        set(value) = prefs.edit { putBoolean("show_translation", value) }

    var pauseOnLookup: Boolean
        get() = prefs.getBoolean("pause_on_lookup", true)
        set(value) = prefs.edit { putBoolean("pause_on_lookup", value) }

    var subtitleSize: Float
        get() = prefs.getFloat("subtitle_size", 26f)
        set(value) = prefs.edit { putFloat("subtitle_size", value) }

    fun position(uri: String): Double = positions.getFloat(uri, 0f).toDouble()

    fun savePosition(uri: String, seconds: Double, duration: Double) {
        positions.edit {
            // finished episodes restart from the beginning
            if (duration > 0 && seconds > duration - 30) remove(uri) else putFloat(uri, seconds.toFloat())
        }
    }

    fun progress(uri: String, duration: Double): Float =
        if (duration <= 0) 0f else (position(uri) / duration).toFloat().coerceIn(0f, 1f)
}
