package app.mizune.desktop

import app.mizune.anki.AnkiConnect
import app.mizune.dictionary.DictionaryCatalog
import app.mizune.dictionary.DictionaryDatabase
import app.mizune.dictionary.DictionaryLookup
import app.mizune.dictionary.JdbcSql
import app.mizune.dictionary.YomitanImporter
import app.mizune.stats.UsageStats
import app.mizune.subs.EmbeddedSubtitleCache
import app.mizune.subs.ImageSubtitles
import app.mizune.subs.MatroskaSubtitleExtractor
import app.mizune.subs.SubtitleFiles
import app.mizune.subs.SubtitleTrack
import app.mizune.ui.AnkiCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile

/** App-wide services: settings, the dictionary, subtitle loading, Anki, updates. */
class DesktopApp(
    private val dataDir: File = AppDirs.data,
    private val cacheDir: File = AppDirs.cache,
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
    /** Started by main, so tests never touch the feed. */
    val updater = Updater(cacheDir, dataDir, ::logError)
    private val usageStats = UsageStats(ANALYTICS_MEASUREMENT_ID, ANALYTICS_API_SECRET)

    /**
     * Counts this install as using the app: once the welcome is done (so turning the counts off
     * there sends nothing at all), then once a day while it stays open. Started by main.
     */
    fun startUsageStats() {
        if (!usageStats.configured) return
        Thread({
            while (!settings.onboarded) Thread.sleep(30_000)
            while (true) {
                if (settings.usageStats) {
                    val platform = when {
                        isMac -> "macos"
                        isWindows -> "windows"
                        else -> "linux"
                    }
                    usageStats.appOpen(settings.installId, platform, System.getProperty("os.version"), APP_VERSION)
                }
                Thread.sleep(24 * 60 * 60 * 1000L)
            }
        }, "usage-stats").apply { isDaemon = true }.start()
    }

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

    /** One import at a time: the bundled JMdict on first launch, then whatever onboarding picked. */
    private val imports = Any()

    /** Where each dictionary [installFromCatalog] was asked for has got to. */
    sealed interface CatalogInstall {
        data object Queued : CatalogInstall
        data class Downloading(val fraction: Float) : CatalogInstall
        data class Importing(val terms: Int) : CatalogInstall
        data object Done : CatalogInstall
        data class Failed(val message: String) : CatalogInstall
    }

    private val _catalogInstalls = MutableStateFlow<Map<String, CatalogInstall>>(emptyMap())
    val catalogInstalls: StateFlow<Map<String, CatalogInstall>> = _catalogInstalls.asStateFlow()

    /** Downloads and imports [entries] one after another in the background, skipping any already under way. */
    fun installFromCatalog(entries: List<DictionaryCatalog.Entry>) {
        val todo = entries.filter { val state = _catalogInstalls.value[it.id]; state == null || state is CatalogInstall.Failed }
        if (todo.isEmpty()) return
        _catalogInstalls.update { it + todo.map { entry -> entry.id to CatalogInstall.Queued } }
        Thread({ todo.forEach(::install) }, "dictionary-install").apply { isDaemon = true }.start()
    }

    private fun install(entry: DictionaryCatalog.Entry) {
        fun status(state: CatalogInstall) = _catalogInstalls.update { it + (entry.id to state) }
        val zip = File(cacheDir, "dictionaries/${entry.id}.zip")
        try {
            DictionaryCatalog.download(entry, zip) { status(CatalogInstall.Downloading(it)) }
            status(CatalogInstall.Queued)
            synchronized(imports) {
                status(CatalogInstall.Importing(0))
                val info = YomitanImporter(database).import(zip.name, { status(CatalogInstall.Importing(it.terms)) }) { zip.inputStream() }
                if (entry == DictionaryCatalog.JITENDEX) {
                    // first in the results, in place of the JMdict it builds on
                    repeat(database.dictionaries().indexOfFirst { it.id == info.id }) { database.move(info.id, -1) }
                    database.dictionaries().filter { it.title.startsWith(DictionaryCatalog.JMDICT_PREFIX) }
                        .forEach { database.setEnabled(it.id, false) }
                }
            }
            status(CatalogInstall.Done)
        } catch (e: Exception) {
            logError("install ${entry.id}", e)
            status(CatalogInstall.Failed(e.message ?: e.toString()))
        } finally {
            zip.delete()
        }
    }

    /** Removes interrupted imports and installs bundled dictionaries not yet installed. Slow; call off the UI thread. */
    fun prepareDictionaries() = synchronized(imports) {
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
                return@synchronized
            }
        }
        _setupStatus.value = null
    }

    /** Sidecar subtitles next to [video], and the text tracks embedded in a Matroska file in languages they don't cover. */
    fun loadSubtitles(video: File): List<SubtitleTrack> {
        val sidecars = sidecarsFor(video).mapNotNull { SubtitleFiles.parseSidecar(it.name, it.readText()) }
        if (!SubtitleFiles.isMatroska(video.name)) return sidecars
        val embedded = runCatching {
            subtitleCache.load(identity(video)) {
                RandomAccessFile(video, "r").use { MatroskaSubtitleExtractor(it.channel).extract() }
            }
        }.getOrElse {
            // the files beside it still play
            if (sidecars.isEmpty()) throw it
            logError("embedded subtitles $video", it)
            emptyList()
        }
        return SubtitleFiles.withEmbedded(sidecars, embedded)
    }

    /**
     * [video]'s image (PGS) subtitles in the target and peek languages, when no text track in
     * [text] covers them: ready at once with their timing, their text read by OCR as they're
     * played (see [app.mizune.subs.PictureLines]) and kept for next time. Scans
     * the file, so call it off the UI thread; the tracks are closed with the session.
     */
    fun openPictureTracks(video: File, text: List<SubtitleTrack>): List<SubtitleTrack> {
        if (!SubtitleFiles.isMatroska(video.name)) return emptyList()
        val languages = listOfNotNull(settings.targetLanguage, settings.peekLanguage).filter { code ->
            ImageSubtitles.model(code) != null && text.none { SubtitleFiles.isLanguage(it, code) }
        }
        if (languages.isEmpty()) return emptyList()
        val images = RandomAccessFile(video, "r").use { MatroskaSubtitleExtractor(it.channel).extractAll().second }
        return ImageSubtitles.tracksToRead(text, images, languages).map { track ->
            val model = ImageSubtitles.model(track)!!
            ImageSubtitles.open(
                track,
                newRecognizer = { TesseractRecognizer(TesseractRecognizer.dataDir(cacheDir, listOf(model)), model) },
                store = subtitleCache.lineStore("${identity(video)}|ocr|${track.name}|${track.lineCount}"),
            )
        }
    }

    private fun sidecarsFor(video: File): List<File> = video.parentFile?.listFiles().orEmpty()
        .filter { it.isFile && SubtitleFiles.isSidecarFor(video.name, it.name) }
        .sortedBy { it.name }

    private fun identity(video: File) = "${video.absolutePath}|${video.length()}|${video.lastModified()}"

    companion object {
        private val BUNDLED = listOf("JMdict_english.zip")
    }
}
