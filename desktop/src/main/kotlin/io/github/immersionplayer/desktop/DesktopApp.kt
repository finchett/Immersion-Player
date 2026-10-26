package io.github.immersionplayer.desktop

import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.dictionary.DictionaryDatabase
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.JdbcSql
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.subs.EmbeddedSubtitleCache
import io.github.immersionplayer.subs.ImageSubtitles
import io.github.immersionplayer.subs.MatroskaSubtitleExtractor
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.subs.SubtitleTrack
import io.github.immersionplayer.ui.AnkiCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.immersionplayer.desktop.mpv.OffscreenGl
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/** Shown in settings; a packaged build carries the same number in its bundle (build.gradle.kts). */
const val APP_VERSION = "0.4.0"

/** App-wide services: settings, the dictionary, subtitle loading, Anki. */
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

    private val _setupStatus = MutableStateFlow<String?>(null)
    /** Progress while a bundled dictionary is being installed, else null. */
    val setupStatus: StateFlow<String?> = _setupStatus.asStateFlow()

    private val _benchmarking = MutableStateFlow(false)
    /** Whether the GPU is being measured for Anime4K's Auto preset. */
    val benchmarking: StateFlow<Boolean> = _benchmarking.asStateFlow()
    private val benchmarkRunning = AtomicBoolean(false)

    /**
     * Measures the GPU for Anime4K's Auto preset, when Anime4K is on at Auto and there's no
     * benchmark yet for this GPU at this screen size. Slow (seconds); call off the UI thread.
     */
    fun benchmarkShadersIfNeeded() {
        if (settings.anime4kPreset != Anime4K.Preset.AUTO) return
        if (!benchmarkRunning.compareAndSet(false, true)) return
        try {
            val gpu = OffscreenGl.gpuName() ?: return
            val (width, height) = screenPixels()
            val last = settings.anime4kBenchmark
            if (last != null && last.gpu == gpu && last.width == width && last.height == height) return
            _benchmarking.value = true
            Anime4K.benchmark(width, height)?.let(settings::updateAnime4kBenchmark)
        } catch (e: Throwable) {
            logError("shader benchmark", e)
        } finally {
            _benchmarking.value = false
            benchmarkRunning.set(false)
        }
    }

    /** The main screen in physical pixels: the most a full-screen video has to fill. */
    private fun screenPixels(): Pair<Int, Int> = runCatching {
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val scale = config.defaultTransform.scaleX
        (config.bounds.width * scale).toInt() to (config.bounds.height * scale).toInt()
    }.getOrDefault(1920 to 1080)

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
        val sidecars = sidecarsFor(video).mapNotNull { SubtitleFiles.parseSidecar(it.name, it.readText()) }
        if (sidecars.isNotEmpty()) return sidecars
        if (!SubtitleFiles.isMatroska(video.name)) return emptyList()
        return subtitleCache.load(identity(video)) {
            RandomAccessFile(video, "r").use { MatroskaSubtitleExtractor(it.channel).extract() }
        }
    }

    /**
     * [video]'s image (PGS) subtitles in the target and peek languages, when no text track in
     * [text] covers them: ready at once with their timing, their text read by OCR as they're
     * played (see [io.github.immersionplayer.subs.PictureLines]) and kept for next time. Scans
     * the file, so call it off the UI thread; the tracks are closed with the session.
     */
    fun openPictureTracks(video: File, text: List<SubtitleTrack>): List<SubtitleTrack> {
        if (!SubtitleFiles.isMatroska(video.name) || sidecarsFor(video).isNotEmpty()) return emptyList()
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
