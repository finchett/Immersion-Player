package io.github.immersionplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.immersionplayer.ui.AppTheme
import java.io.File
import java.security.MessageDigest
import java.util.prefs.Preferences

/** Where the app keeps its database and caches, per OS convention. */
object AppDirs {
    val data: File by lazy {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            os.contains("mac") -> File(home, "Library/Application Support/Immersion Player")
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Immersion Player")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "immersion-player")
        }
        dir.apply { mkdirs() }
    }

    val cache: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val dir = when {
            os.contains("mac") -> File(home, "Library/Caches/Immersion Player")
            os.contains("win") -> File(data, "cache")
            else -> File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", "immersion-player")
        }
        dir.apply { mkdirs() }
    }
}

/**
 * User settings and per-video state, in java.util.prefs (plist on macOS, registry on Windows,
 * ~/.java on Linux). Settings the UI reads are Compose state so changes apply immediately.
 */
class Settings(node: String = "io/github/immersionplayer") {
    private val prefs = Preferences.userRoot().node(node)
    private val videos = prefs.node("videos")

    /** A fixed theme, or null to follow the system's light/dark appearance. */
    var theme by mutableStateOf(AppTheme.entries.firstOrNull { it.name == prefs.get("theme", null) })
        private set
    var subtitleSize by mutableStateOf(prefs.getFloat("subtitle_size", 30f))
        private set
    var panelFraction by mutableStateOf(prefs.getFloat("panel_fraction", 0.36f))
        private set
    var autoPause by mutableStateOf(prefs.getBoolean("auto_pause", false))
        private set
    var pauseOnLookup by mutableStateOf(prefs.getBoolean("pause_on_lookup", true))
        private set
    var copyLines by mutableStateOf(prefs.getBoolean("copy_lines", false))
        private set
    var libraryRoot by mutableStateOf(prefs.get("library_root", null))
        private set

    fun updateTheme(value: AppTheme?) {
        theme = value
        if (value == null) prefs.remove("theme") else prefs.put("theme", value.name)
    }
    fun updateSubtitleSize(value: Float) { subtitleSize = value; prefs.putFloat("subtitle_size", value) }
    fun updatePanelFraction(value: Float) { panelFraction = value; prefs.putFloat("panel_fraction", value) }
    fun updateAutoPause(value: Boolean) { autoPause = value; prefs.putBoolean("auto_pause", value) }
    fun updatePauseOnLookup(value: Boolean) { pauseOnLookup = value; prefs.putBoolean("pause_on_lookup", value) }
    fun updateCopyLines(value: Boolean) { copyLines = value; prefs.putBoolean("copy_lines", value) }
    fun updateLibraryRoot(value: String?) {
        libraryRoot = value
        if (value == null) prefs.remove("library_root") else prefs.put("library_root", value)
    }

    fun isBundledInstalled(name: String) = prefs.getBoolean("bundled:$name", false)
    fun setBundledInstalled(name: String) = prefs.putBoolean("bundled:$name", true)

    // per video, keyed by a hash of the path (Preferences keys are limited to 80 characters)

    fun position(path: String): Double = videos.getDouble("${key(path)}.pos", 0.0)
    fun duration(path: String): Double = videos.getDouble("${key(path)}.dur", 0.0)

    fun savePosition(path: String, seconds: Double, duration: Double) {
        val k = key(path)
        if (duration > 0) videos.putDouble("$k.dur", duration)
        // finished episodes restart from the beginning
        if (duration > 0 && seconds > duration - 30) videos.remove("$k.pos") else videos.putDouble("$k.pos", seconds)
    }

    fun progress(path: String): Float {
        val duration = duration(path)
        return if (duration <= 0) 0f else (position(path) / duration).toFloat().coerceIn(0f, 1f)
    }

    fun subtitleOffset(path: String): Double = videos.getDouble("${key(path)}.offset", 0.0)
    fun setSubtitleOffset(path: String, seconds: Double) = videos.putDouble("${key(path)}.offset", seconds)

    fun trackChoice(path: String, role: String): String? = videos.get("${key(path)}.$role", null)
    fun setTrackChoice(path: String, role: String, name: String) = videos.put("${key(path)}.$role", name)

    private fun key(path: String): String =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
