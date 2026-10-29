package app.mizune.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.mizune.desktop.mpv.MpvPlayer
import app.mizune.dictionary.YomitanImporter
import app.mizune.ui.AppTheme
import app.mizune.ui.ThemeFamily
import app.mizune.ui.ThemeMode
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences
import javax.imageio.ImageIO

/**
 * The README's screenshots, at twice the size for sharp screens. Needs:
 * - MIZUNE_README_FILMS: Sintel.mkv with Sintel.ja.srt and Sintel.en.srt, and any other films
 *   (Blender's open movies) for the library;
 * - MIZUNE_README_DICTS: jitendex.zip and jpdb.zip;
 * - MIZUNE_README_SHOTS: where the pictures go.
 * Skips itself otherwise.
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeScreenshots {
    private val films = System.getenv("MIZUNE_README_FILMS")?.let(::File)
    private val dicts = System.getenv("MIZUNE_README_DICTS")?.let(::File)
    private val out = System.getenv("MIZUNE_README_SHOTS")?.let(::File)?.apply { mkdirs() }

    private val width = 1440
    private val height = 860
    private val scale = 2f

    private fun SemanticsNodeInteractionsProvider.shot(name: String) {
        val image = onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(out, "$name.png"))
    }

    private fun SemanticsNodeInteractionsProvider.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun takeThem() {
        assumeTrue("set MIZUNE_README_FILMS, _DICTS and _SHOTS", films?.isDirectory == true && dicts?.isDirectory == true && out != null)
        val films = films!!
        val dir = Files.createTempDirectory("mizune-readme").toFile()
        // the library: each film in a folder of its own, named the way they're published
        val library = File(dir, "Films")
        val sintel = File(library, "Sintel (2010)/Sintel.mkv")
        fun link(source: String, target: File) {
            val file = File(films, source).takeIf { it.isFile } ?: return
            target.parentFile.mkdirs()
            Files.createSymbolicLink(target.toPath(), file.absoluteFile.toPath())
        }
        link("Sintel.mkv", sintel)
        link("Sintel.ja.srt", File(sintel.parentFile, "Sintel.ja.srt"))
        link("Sintel.en.srt", File(sintel.parentFile, "Sintel.en.srt"))
        link("ToS.mov", File(library, "Tears of Steel (2012)/Tears of Steel.mov"))
        link("ED.mov", File(library, "Elephants Dream (2006)/Elephants Dream.mov"))
        link("BigBuckBunny_640x360.m4v", File(library, "Big Buck Bunny (2008)/Big Buck Bunny.m4v"))
        link("caminandes_gran_dillama.mp4", File(library, "Caminandes (2013)/02 Gran Dillama.mp4"))

        val node = "app/mizune-readme"
        Preferences.userRoot().node(node).removeNode()
        val settings = Settings(node).apply {
            // the first-play tutorial would cover the player
            updatePlayerTutorialSeen(true)
            updateLibraryRoot(library.path)
            updatePeekLanguage("en")
            savePosition(sintel.absolutePath, 420.0, 888.0)
        }
        val app = DesktopApp(File(dir, "data").apply { mkdirs() }, File(dir, "cache"), settings)
        try {
            // the welcome first, while its dictionaries are still to install
            welcomeAndThemes(app)
            for (name in listOf("jitendex.zip", "jpdb.zip")) {
                YomitanImporter(app.database).import(name, {}) { File(dicts, name).inputStream() }
            }
            library(app, library)
            player(app, sintel)
        } finally {
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }

    private fun library(app: DesktopApp, root: File) = runDesktopComposeUiTest(width = (width * scale).toInt(), height = (440 * scale).toInt()) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                DesktopTheme(AppTheme.Celadon) {
                    Chrome(
                        header = { LibraryHeader(app, null, onOpenFolder = {}, onOpenSettings = {}) },
                        body = { LibraryBody(app, null, onOpenFolder = {}, onOpenVideo = {}) },
                    )
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { shows("Sintel") }
        // covers come from mpv in the background
        Thread.sleep(12_000)
        waitForIdle()
        shot("library")
    }

    private fun player(app: DesktopApp, video: File) {
        val player = MpvPlayer()
        player.property("mute", "yes")
        val session = PlayerSession(app.settings, player, video)
        val keys = PlayerKeys()
        try {
            runDesktopComposeUiTest(width = (width * scale).toInt(), height = (height * scale).toInt()) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(scale)) {
                        DesktopTheme(AppTheme.Jade) {
                            PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {})
                        }
                    }
                }
                // the line and its first word, looked up on its own
                waitUntil(timeoutMillis = 20_000) { session.primary.value?.cues.orEmpty().isNotEmpty() }
                val alone = session.primary.value!!.cues.indexOfFirst { it.text.startsWith("私は物心") }
                session.playLine(alone, stopAtEnd = true)
                waitUntil(timeoutMillis = 15_000) { session.stoppedAtLine.value }
                // 物心, the third character of the line's first row of sixteen or so
                onNodeWithText("私は物心", substring = true).performMouseInput {
                    click(Offset(this.width * 2.5f / 16f, this.height * 0.25f))
                }
                waitUntil(timeoutMillis = 5_000) { shows("ものごころ") }
                Thread.sleep(1_500)
                waitForIdle()
                shot("player")

                // the panel put away, and a word looked up over the video
                val searching = session.primary.value!!.cues.indexOfFirst { it.text.startsWith("探して") }
                app.settings.updatePanelHidden(true)
                session.playLine(searching, stopAtEnd = true)
                waitUntil(timeoutMillis = 15_000) { session.stoppedAtLine.value && shows("探しているんです") }
                Thread.sleep(800)
                onNodeWithText("探しているんです", substring = true).performMouseInput {
                    moveTo(Offset(this.width * 0.05f, this.height / 2f))
                }
                waitUntil(timeoutMillis = 5_000) { shows("search") }
                Thread.sleep(800)
                waitForIdle()
                shot("over-video")
                app.settings.updatePanelHidden(false)
            }
        } finally {
            session.close()
            player.destroy()
        }
    }

    private fun welcomeAndThemes(app: DesktopApp) = runDesktopComposeUiTest(width = (1100 * scale).toInt(), height = (720 * scale).toInt()) {
        // the welcome's mascot bobs for ever, so time moves only when told to
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                DesktopTheme(AppTheme.Celadon) {
                    Onboarding(app, onFinish = {}, start = OnboardingStep.Dictionaries)
                }
            }
        }
        mainClock.advanceTimeBy(1_500)
        shot("welcome")
        app.settings.updateThemeFamily(ThemeFamily.Jade)
        app.settings.updateThemeMode(ThemeMode.Auto)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                DesktopTheme(AppTheme.Jade) {
                    Chrome(
                        header = { SettingsHeader(SettingsPane.Appearance) },
                        body = { SettingsBody(app, SettingsPane.Appearance) },
                        leading = { SettingsSidebar(app, SettingsPane.Appearance, onPane = {}, onBack = {}) },
                    )
                }
            }
        }
        mainClock.advanceTimeBy(1_500)
        shot("themes")
    }
}
