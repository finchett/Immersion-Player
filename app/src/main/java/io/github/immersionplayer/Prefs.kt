package io.github.immersionplayer

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val positions = context.getSharedPreferences("positions", Context.MODE_PRIVATE)
    private val perVideo = context.getSharedPreferences("per_video", Context.MODE_PRIVATE)

    var libraryTreeUri: String?
        get() = prefs.getString("library_tree_uri", null)
        set(value) = prefs.edit { putString("library_tree_uri", value) }

    /** Use the phone's shoulder triggers (via Shizuku) to step through lines. */
    var shoulderTriggers: Boolean
        get() = prefs.getBoolean("shoulder_triggers", true)
        set(value) = prefs.edit { putBoolean("shoulder_triggers", value) }

    var theme: String?
        get() = prefs.getString("theme", null)
        set(value) = prefs.edit { putString("theme", value) }

    /** Show the video and panel as rounded cards. */
    var roundedCorners: Boolean
        get() = prefs.getBoolean("rounded_corners", false)
        set(value) = prefs.edit { putBoolean("rounded_corners", value) }

    /** Folder names from the library root to the last opened folder, joined by "/". */
    var libraryPath: String?
        get() = prefs.getString("library_path", null)
        set(value) = prefs.edit { putString("library_path", value) }

    /** Pause when the current line ends (mpvacious "play up to next"). */
    var autoPause: Boolean
        get() = prefs.getBoolean("auto_pause", false)
        set(value) = prefs.edit { putBoolean("auto_pause", value) }

    /** Copy each new line to the clipboard, for use with an external dictionary. */
    var copyLines: Boolean
        get() = prefs.getBoolean("copy_lines", false)
        set(value) = prefs.edit { putBoolean("copy_lines", value) }

    var pauseOnLookup: Boolean
        get() = prefs.getBoolean("pause_on_lookup", true)
        set(value) = prefs.edit { putBoolean("pause_on_lookup", value) }

    /** Zoom the video to fill its area (mpv panscan) instead of fitting it. */
    var videoFill: Boolean
        get() = prefs.getBoolean("video_fill", false)
        set(value) = prefs.edit { putBoolean("video_fill", value) }

    /** Whether the first-run gesture guide has been dismissed. */
    var gestureGuideSeen: Boolean
        get() = prefs.getBoolean("gesture_guide_seen", false)
        set(value) = prefs.edit { putBoolean("gesture_guide_seen", value) }

    /** Share of the screen height used by the video in portrait. */
    var portraitVideoFraction: Float
        get() = prefs.getFloat("portrait_video_fraction", 0.34f)
        set(value) = prefs.edit { putFloat("portrait_video_fraction", value) }

    /** Share of the screen width used by the study panel in landscape. */
    var panelFraction: Float
        get() = prefs.getFloat("panel_fraction", 0.37f)
        set(value) = prefs.edit { putFloat("panel_fraction", value) }

    var subtitleSize: Float
        get() = prefs.getFloat("subtitle_size", 26f)
        set(value) = prefs.edit { putFloat("subtitle_size", value) }

    /** Position a video's library thumbnail was taken at, to know when to refresh it. */
    fun thumbnailPosition(uri: String): Double = perVideo.getFloat("thumbpos:$uri", -1f).toDouble()

    fun setThumbnailPosition(uri: String, seconds: Double) =
        perVideo.edit { putFloat("thumbpos:$uri", seconds.toFloat()) }

    /** Seconds to shift a video's subtitles (positive = subtitles appear later). */
    fun subtitleOffset(uri: String): Double = perVideo.getFloat("offset:$uri", 0f).toDouble()

    fun setSubtitleOffset(uri: String, seconds: Double) =
        perVideo.edit { putFloat("offset:$uri", seconds.toFloat()) }

    /** Name of the track picked for a video; "" means "none" (translation only), null means not chosen. */
    fun trackChoice(uri: String, role: String): String? = perVideo.getString("$role:$uri", null)

    fun setTrackChoice(uri: String, role: String, name: String) =
        perVideo.edit { putString("$role:$uri", name) }

    fun position(uri: String): Double = positions.getFloat(uri, 0f).toDouble()

    /** Length of a video, once it has been opened at least once (0 if unknown). */
    fun duration(uri: String): Double = perVideo.getFloat("duration:$uri", 0f).toDouble()

    fun savePosition(uri: String, seconds: Double, duration: Double) {
        if (duration > 0) perVideo.edit { putFloat("duration:$uri", duration.toFloat()) }
        positions.edit {
            // finished episodes restart from the beginning
            if (duration > 0 && seconds > duration - 30) remove(uri) else putFloat(uri, seconds.toFloat())
        }
    }

    fun progress(uri: String, duration: Double): Float =
        if (duration <= 0) 0f else (position(uri) / duration).toFloat().coerceIn(0f, 1f)
}
