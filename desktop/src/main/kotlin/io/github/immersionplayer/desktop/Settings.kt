package io.github.immersionplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.anki.AnkiException
import io.github.immersionplayer.anki.CardFormat
import io.github.immersionplayer.anki.CardFormats
import io.github.immersionplayer.anki.CardTarget
import io.github.immersionplayer.ui.AppTheme
import org.json.JSONObject
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
    /** Crop the video to fill its area (mpv panscan) instead of letterboxing. */
    var videoFill by mutableStateOf(prefs.getBoolean("video_fill", false))
        private set
    /** Video and panel as separate rounded cards with a gap between them. */
    var roundedCorners by mutableStateOf(prefs.getBoolean("rounded_corners", false))
        private set
    /** Language being studied: its subtitles are the line. */
    var targetLanguage by mutableStateOf(prefs.get("target_language", "ja"))
        private set
    /** Language shown while peeking, or null for none. */
    var peekLanguage by mutableStateOf(prefs.get("peek_language", "en").ifEmpty { null })
        private set

    // --- Anki ---

    /** Making Anki cards from the player: off until turned on in settings. */
    var ankiEnabled by mutableStateOf(prefs.getBoolean("anki_enabled", false))
        private set
    /** Where AnkiConnect listens. */
    var ankiUrl by mutableStateOf(prefs.get("anki_url", AnkiConnect.DEFAULT_URL))
        private set
    var ankiDeck by mutableStateOf(prefs.get("anki_deck", null))
        private set
    var ankiNoteType by mutableStateOf(prefs.get("anki_note_type", null))
        private set
    /** Tags for new cards, separated by spaces. */
    var ankiTags by mutableStateOf(prefs.get("anki_tags", "immersion-player"))
        private set
    /** Seconds of audio kept before and after the line. */
    var ankiAudioPadding by mutableStateOf(prefs.getDouble("anki_audio_padding", 0.3))
        private set
    /** The screenshot moves: the whole line, animated, instead of the one frame on screen. */
    var ankiImageAnimated by mutableStateOf(prefs.getBoolean("anki_image_animated", true))
        private set
    /** Height of the screenshot in pixels. */
    var ankiImageHeight by mutableStateOf(prefs.getInt("anki_image_height", 360))
        private set
    /** The fields of each note type used so far, so switching back keeps its setup. */
    private var ankiFormats by mutableStateOf(runCatching { JSONObject(prefs.get("anki_formats", "{}")) }.getOrElse { JSONObject() })

    /** How the chosen note type's fields are filled, once set up. */
    val ankiFormat: CardFormat?
        get() = ankiNoteType?.let(::cardFormat)

    /** Where new cards go, or an [AnkiException] saying what still has to be chosen. */
    fun cardTarget(): CardTarget {
        val format = ankiFormat ?: throw AnkiException("Choose a note type under Settings › Anki")
        val deck = ankiDeck ?: throw AnkiException("Choose a deck under Settings › Anki")
        return CardTarget(format, deck, ankiTags.split(' ', '\u3000').filter { it.isNotBlank() })
    }

    fun cardFormat(noteType: String): CardFormat? =
        ankiFormats.optJSONObject(noteType)?.let { CardFormats.upgrade(CardFormat.fromJson(noteType, it)) }

    fun saveCardFormat(format: CardFormat) {
        val all = JSONObject(ankiFormats.toString()).put(format.noteType, format.toJson())
        ankiFormats = all
        prefs.put("anki_formats", all.toString())
    }

    fun updateAnkiEnabled(value: Boolean) { ankiEnabled = value; prefs.putBoolean("anki_enabled", value) }
    fun updateAnkiUrl(value: String) { ankiUrl = value; prefs.put("anki_url", value) }
    fun updateAnkiDeck(value: String) { ankiDeck = value; prefs.put("anki_deck", value) }
    fun updateAnkiNoteType(value: String) { ankiNoteType = value; prefs.put("anki_note_type", value) }
    fun updateAnkiTags(value: String) { ankiTags = value; prefs.put("anki_tags", value) }
    fun updateAnkiAudioPadding(value: Double) { ankiAudioPadding = value; prefs.putDouble("anki_audio_padding", value) }
    fun updateAnkiImageAnimated(value: Boolean) { ankiImageAnimated = value; prefs.putBoolean("anki_image_animated", value) }
    fun updateAnkiImageHeight(value: Int) { ankiImageHeight = value; prefs.putInt("anki_image_height", value) }

    fun updateTheme(value: AppTheme?) {
        theme = value
        if (value == null) prefs.remove("theme") else prefs.put("theme", value.name)
    }
    fun updateSubtitleSize(value: Float) { subtitleSize = value; prefs.putFloat("subtitle_size", value) }
    fun updatePanelFraction(value: Float) { panelFraction = value; prefs.putFloat("panel_fraction", value) }
    fun updateAutoPause(value: Boolean) { autoPause = value; prefs.putBoolean("auto_pause", value) }
    fun updatePauseOnLookup(value: Boolean) { pauseOnLookup = value; prefs.putBoolean("pause_on_lookup", value) }
    fun updateCopyLines(value: Boolean) { copyLines = value; prefs.putBoolean("copy_lines", value) }
    fun updateVideoFill(value: Boolean) { videoFill = value; prefs.putBoolean("video_fill", value) }
    fun updateRoundedCorners(value: Boolean) { roundedCorners = value; prefs.putBoolean("rounded_corners", value) }
    fun updateTargetLanguage(code: String) { targetLanguage = code; prefs.put("target_language", code) }
    fun updatePeekLanguage(code: String?) { peekLanguage = code; prefs.put("peek_language", code ?: "") }
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


    private fun key(path: String): String =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
