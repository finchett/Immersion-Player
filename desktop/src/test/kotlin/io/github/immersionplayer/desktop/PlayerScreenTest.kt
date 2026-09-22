package io.github.immersionplayer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.ui.AppTheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Drives the real player screen offscreen: real libmpv, sidecar subtitles and a tiny dictionary.
 * Needs a video (any codec mpv plays) in IMMERSION_TEST_VIDEO; skips itself otherwise.
 * Set IMMERSION_TEST_SHOTS to a directory to save screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerScreenTest {

    private val videoSource = System.getenv("IMMERSION_TEST_VIDEO")?.let(::File)
    private val shots = System.getenv("IMMERSION_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }

    private fun srt(vararg lines: Pair<ClosedRange<Double>, String>) = lines.mapIndexed { i, (range, text) ->
        "${i + 1}\n${time(range.start)} --> ${time(range.endInclusive)}\n$text\n"
    }.joinToString("\n")

    private fun time(seconds: Double): String {
        val ms = (seconds * 1000).toLong()
        return "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    }

    private fun dictionaryZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            put("index.json", """{"title":"Test","revision":"1","format":3}""")
            put(
                "term_bank_1.json",
                """[
                ["今日","きょう","n","",0,["today"],1,""],
                ["何","なに","pn","",0,["what"],2,""],
                ["食べる","たべる","v1","v1",0,["to eat"],3,""],
                ["勉強","べんきょう","n vs","vs",0,["study"],4,""]
            ]""",
            )
        }
        return out.toByteArray()
    }

    private fun SemanticsNodeInteractionsProvider.shot(name: String) {
        val dir = shots ?: return
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun SemanticsNodeInteractionsProvider.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun playsLinesAndLooksUpWords() {
        assumeTrue("set IMMERSION_TEST_VIDEO", videoSource?.isFile == true)
        val dir = Files.createTempDirectory("immersion-test").toFile()
        val video = File(dir, "Lesson 01.mkv")
        Files.createSymbolicLink(video.toPath(), videoSource!!.absoluteFile.toPath())
        File(dir, "Lesson 01.ja.srt").writeText(
            srt(1.0..4.0 to "今日は何を食べたい？", 5.0..8.0 to "食べさせられなかった。", 9.0..12.0 to "勉強しましょう。")
        )
        File(dir, "Lesson 01.en.srt").writeText(
            srt(1.0..4.0 to "What do you want to eat today?", 5.0..8.0 to "I couldn't make them eat.", 9.0..12.0 to "Let's study.")
        )

        val node = "io/github/immersionplayer-test"
        Preferences.userRoot().node(node).removeNode()
        val app = DesktopApp(File(dir, "data").apply { mkdirs() }, File(dir, "cache"), Settings(node))
        YomitanImporter(app.database).import("test.zip", {}) { dictionaryZip().inputStream() }

        val player = MpvPlayer()
        player.property("mute", "yes")
        val session = PlayerSession(app.settings, player, video)
        val keys = PlayerKeys()
        try {
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    MaterialTheme(colorScheme = AppTheme.Midnight.scheme) {
                        PlayerScreen(app, session, keys, onBack = {})
                    }
                }
                // first line plays, and its first kanji word is looked up without a click
                waitUntil(timeoutMillis = 15_000) { shows("今日は何を食べたい") && shows("today") }
                session.pause()
                shot("1-first-line")

                // next line: the inflected verb resolves to its dictionary form
                session.nextLine()
                waitUntil(timeoutMillis = 10_000) { shows("食べさせられなかった") && shows("to eat") }
                session.pause()
                shot("2-deinflected")

                // hold E: the English replaces the Japanese
                keys.peeking = true
                waitUntil(timeoutMillis = 5_000) { shows("I couldn't make them eat.") }
                mainClock.advanceTimeBy(500)
                shot("3-english")
                keys.peeking = false

                session.nextLine()
                waitUntil(timeoutMillis = 10_000) { shows("勉強しましょう") && shows("study") }
            }
        } finally {
            session.close()
            player.destroy()
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
