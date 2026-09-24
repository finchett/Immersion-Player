package io.github.immersionplayer.desktop

import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.dictionary.DictionaryDatabase
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.JdbcSql
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.subs.EmbeddedSubtitleCache
import io.github.immersionplayer.subs.MatroskaSubtitleExtractor
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.subs.SubtitleTrack
import io.github.immersionplayer.ui.AnkiCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/** Shown in settings; a packaged build carries the same number in its bundle (build.gradle.kts). */
const val APP_VERSION = "0.2.0"

/** App-wide services: settings, the dictionary, subtitle loading, Anki. */
class DesktopApp(
    private val dataDir: File = AppDirs.data,
    cacheDir: File = AppDirs.cache,
    val settings: Settings = Settings(),
) {
    val database = DictionaryDatabase { JdbcSql("jdbc:sqlite:" + File(dataDir, "dictionaries.db").path) }
    val lookup = DictionaryLookup(database)
    private val subtitleCache = EmbeddedSubtitleCache(File(cacheDir, "subtitles"))
    val thumbnails = Thumbnails(cacheDir)
    val anki = AnkiCards(
        connect = { AnkiConnect(settings.ankiUrl) },
        target = settings::cardTarget,
        mediaDir = File(cacheDir, "anki"),
        logError = ::logError,
    )

    private val _setupStatus = MutableStateFlow<String?>(null)
    /** Progress while a bundled dictionary is being installed, else null. */
    val setupStatus: StateFlow<String?> = _setupStatus.asStateFlow()

    fun logError(context: String, error: Throwable) {
        runCatching {
            val file = File(dataDir, "errors.log")
            if (file.length() > 256 * 1024) file.delete()
            file.appendText("${java.util.Date()} $context\n${error.stackTraceToString()}\n")
        }
    }

    /** Removes interrupted imports and installs bundled dictionaries not yet installed. Slow; call off the UI thread. */
    fun prepareDictionaries() {
        database.deleteIncomplete()
        for (name in BUNDLED) {
            if (settings.isBundledInstalled(name)) continue
            val label = name.removeSuffix(".zip").replace('_', ' ')
            val resource = "/dictionaries/$name"
            if (javaClass.getResource(resource) == null) continue
            try {
                _setupStatus.value = "Setting up $label…"
                YomitanImporter(database).import(name, { progress ->
                    _setupStatus.value = "Setting up $label… ${progress.terms} terms"
                }) { javaClass.getResourceAsStream(resource)!! }
                settings.setBundledInstalled(name)
            } catch (e: Exception) {
                logError("bundled $name", e)
                _setupStatus.value = "Couldn't set up $label: ${e.message}"
                return
            }
        }
        _setupStatus.value = null
    }

    /** Sidecar subtitles next to [video] win; otherwise the text tracks embedded in a Matroska file. */
    fun loadSubtitles(video: File): List<SubtitleTrack> {
        val sidecars = video.parentFile?.listFiles().orEmpty()
            .filter { it.isFile && SubtitleFiles.isSidecarFor(video.name, it.name) }
            .sortedBy { it.name }
            .mapNotNull { SubtitleFiles.parseSidecar(it.name, it.readText()) }
        if (sidecars.isNotEmpty()) return sidecars
        if (!SubtitleFiles.isMatroska(video.name)) return emptyList()
        return subtitleCache.load("${video.absolutePath}|${video.length()}|${video.lastModified()}") {
            RandomAccessFile(video, "r").use { MatroskaSubtitleExtractor(it.channel).extract() }
        }
    }

    companion object {
        private val BUNDLED = listOf("JMdict_english.zip")
    }
}
