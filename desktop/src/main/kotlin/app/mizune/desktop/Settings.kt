package app.mizune.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mizune.anki.AnkiConnect
import app.mizune.anki.AnkiException
import app.mizune.anki.CardFormat
import app.mizune.anki.CardFormats
import app.mizune.anki.CardTarget
import app.mizune.library.Titles
import app.mizune.library.isVideoName
import app.mizune.ui.AppTheme
import app.mizune.ui.ThemeFamily
import app.mizune.ui.ThemeMode
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
            os.contains("mac") -> File(home, "Library/Application Support/Mizune")
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Mizune")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "mizune")
        }
        dir.apply { mkdirs() }
    }

    val cache: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val dir = when {
            os.contains("mac") -> File(home, "Library/Caches/Mizune")
            os.contains("win") -> File(data, "cache")
            else -> File(System.getenv("XDG_CACHE_HOME") ?: "$home/.cache", "mizune")
        }
        dir.apply { mkdirs() }
    }
}

/**
 * User settings and per-video state, in java.util.prefs (plist on macOS, registry on Windows,
 * ~/.java on Linux). Settings the UI reads are Compose state so changes apply immediately.
 * -Dmizune.prefs=<node> keeps a separate set in place of the app's own, to try a first launch.
 */
class Settings(node: String = System.getProperty("mizune.prefs") ?: "app/mizune") {
    private val prefs = Preferences.userRoot().node(node)
    private val videos = prefs.node("videos")

    /**
     * The theme family, and whether it's shown light, dark or as the system is. Before families
     * there was one theme, or none to follow the system, kept as "theme"; that carries over.
     */
    var themeFamily by mutableStateOf(
        ThemeFamily.entries.firstOrNull { it.name == prefs.get("theme_family", null) }
            ?: oldTheme()?.let(ThemeFamily::of) ?: ThemeFamily.Jade,
    )
        private set
    var themeMode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.get("theme_mode", null) }
            ?: oldTheme()?.let { if (ThemeFamily.of(it).dark == it) ThemeMode.Dark else ThemeMode.Light } ?: ThemeMode.Auto,
    )
        private set

    private fun oldTheme() = AppTheme.entries.firstOrNull { it.name == prefs.get("theme", null) }

    /** The app icon in the Dock, and on Windows and Linux the window's: light, dark, or as the theme is. */
    var appIcon by mutableStateOf(AppIcon.entries.firstOrNull { it.name == prefs.get("app_icon", null) } ?: AppIcon.Light)
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
    /** Each video's place in its folder, on its thumbnail. */
    var episodeNumbers by mutableStateOf(prefs.getBoolean("episode_numbers", true))
        private set
    /** Show and folder names without release tags, and episodes as "Episode 1" (see [Titles]). */
    var tidyNames by mutableStateOf(prefs.getBoolean("tidy_names", true))
        private set
    /**
     * The study panel put away (Full): the video takes the window, the line goes over it, and the
     * controls keep out of sight until the pointer reaches an edge.
     */
    var panelHidden by mutableStateOf(prefs.getBoolean("panel_hidden", false))
        private set
    /** Crop the video to fill its area (mpv panscan) instead of letterboxing. */
    var videoFill by mutableStateOf(prefs.getBoolean("video_fill", false))
        private set
    /** Anime4K on the video, and how heavy. */
    var anime4kPreset by mutableStateOf(
        Anime4K.Preset.entries.firstOrNull { it.name == prefs.get("anime4k_preset", null) } ?: Anime4K.Preset.OFF,
    )
        private set
    /** Advanced: Anime4K's mode, or null to follow each video's resolution. */
    var anime4kMode by mutableStateOf(Anime4K.Mode.entries.firstOrNull { it.name == prefs.get("anime4k_advanced_mode", null) })
        private set
    /** Whether Anime4K runs its lighter networks, with Auto settled. */
    val anime4kFast: Boolean get() = Anime4K.fast(anime4kPreset, Anime4K.hardware)
    /** Video and panel as separate rounded cards with a gap between them; on by default on macOS. */
    var roundedCorners by mutableStateOf(prefs.getBoolean("rounded_corners", isMac))
        private set
    /** Language being studied: its subtitles are the line. */
    var targetLanguage by mutableStateOf(prefs.get("target_language", "ja"))
        private set
    /** Language shown while peeking, or null for none. */
    var peekLanguage by mutableStateOf(prefs.get("peek_language", "en").ifEmpty { null })
        private set
    /** Whether a peek language (or none) has been chosen, rather than English by default. */
    val peekLanguageChosen: Boolean get() = prefs.get("peek_language", null) != null

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
    var ankiTags by mutableStateOf(prefs.get("anki_tags", "mizune"))
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

    fun updateThemeFamily(value: ThemeFamily) { themeFamily = value; prefs.put("theme_family", value.name) }
    fun updateThemeMode(value: ThemeMode) { themeMode = value; prefs.put("theme_mode", value.name) }
    fun updateAppIcon(value: AppIcon) { appIcon = value; prefs.put("app_icon", value.name) }
    fun updateSubtitleSize(value: Float) { subtitleSize = value; prefs.putFloat("subtitle_size", value) }
    fun updatePanelFraction(value: Float) { panelFraction = value; prefs.putFloat("panel_fraction", value) }
    fun updateAutoPause(value: Boolean) { autoPause = value; prefs.putBoolean("auto_pause", value) }
    fun updatePauseOnLookup(value: Boolean) { pauseOnLookup = value; prefs.putBoolean("pause_on_lookup", value) }
    fun updateCopyLines(value: Boolean) { copyLines = value; prefs.putBoolean("copy_lines", value) }
    fun updateEpisodeNumbers(value: Boolean) { episodeNumbers = value; prefs.putBoolean("episode_numbers", value) }
    fun updateTidyNames(value: Boolean) { tidyNames = value; prefs.putBoolean("tidy_names", value) }

    /** How a file or folder called [name] is shown. */
    fun title(name: String): String = if (tidyNames) Titles.clean(name) else if (isVideoName(name)) name.substringBeforeLast('.') else name
    fun updatePanelHidden(value: Boolean) { panelHidden = value; prefs.putBoolean("panel_hidden", value) }
    fun updateVideoFill(value: Boolean) { videoFill = value; prefs.putBoolean("video_fill", value) }
    fun updateAnime4kPreset(value: Anime4K.Preset) { anime4kPreset = value; prefs.put("anime4k_preset", value.name) }
    fun updateAnime4kMode(value: Anime4K.Mode?) {
        anime4kMode = value
        if (value == null) prefs.remove("anime4k_advanced_mode") else prefs.put("anime4k_advanced_mode", value.name)
    }
    fun updateRoundedCorners(value: Boolean) { roundedCorners = value; prefs.putBoolean("rounded_corners", value) }
    fun updateTargetLanguage(code: String) { targetLanguage = code; prefs.put("target_language", code) }
    fun updatePeekLanguage(code: String?) { peekLanguage = code; prefs.put("peek_language", code ?: "") }
    fun updateLibraryRoot(value: String?) {
        libraryRoot = value
        if (value == null) prefs.remove("library_root") else prefs.put("library_root", value)
    }

    /** Whether the welcome has run; anyone who'd picked a library before it existed has seen enough. */
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", libraryRoot != null))
        private set
    fun finishOnboarding() { onboarded = true; prefs.putBoolean("onboarded", true) }

    /** Whether the anonymous usage counts are sent (UsageStats); on unless turned off. */
    var usageStats by mutableStateOf(prefs.getBoolean("usage_stats", true))
        private set
    fun updateUsageStats(value: Boolean) { usageStats = value; prefs.putBoolean("usage_stats", value) }

    /** A random id for this install, made the first time it's asked for; all the counts know of anyone. */
    val installId: String by lazy {
        prefs.get("install_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.put("install_id", it) }
    }

    /** Whether the first-play tutorial has been through (PlayerTutorial); Settings › Keyboard brings it back. */
    var playerTutorialSeen by mutableStateOf(prefs.getBoolean("player_tutorial_seen", false))
        private set
    fun updatePlayerTutorialSeen(value: Boolean) { playerTutorialSeen = value; prefs.putBoolean("player_tutorial_seen", value) }

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

    /** Name of the track picked for a video; "" means "none" (translation only), null means not chosen. */
    fun trackChoice(path: String, role: String): String? = videos.get("${key(path)}.$role", null)
    fun setTrackChoice(path: String, role: String, name: String) = videos.put("${key(path)}.$role", name)


    private fun key(path: String): String =
        MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Which app icon to show: the one matching the theme's light or dark side, or one of them always. */
enum class AppIcon(val label: String) { Theme("Match theme"), Light("Light"), Dark("Dark") }
