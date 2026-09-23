package io.github.immersionplayer.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    /** A temp library holding one video and its subtitles, with a player and session on it. */
    private class Fixture(val app: DesktopApp, val session: PlayerSession)

    private val node = "io/github/immersionplayer-test"

    /** Builds that library, runs [block] against it, and takes it all down again afterwards. */
    private fun fixture(ja: String, en: String, block: Fixture.() -> Unit) {
        val dir = Files.createTempDirectory("immersion-test").toFile()
        val video = File(dir, "Lesson 01.mkv")
        Files.createSymbolicLink(video.toPath(), videoSource!!.absoluteFile.toPath())
        File(dir, "Lesson 01.ja.srt").writeText(ja)
        File(dir, "Lesson 01.en.srt").writeText(en)

        Preferences.userRoot().node(node).removeNode()
        val app = DesktopApp(File(dir, "data").apply { mkdirs() }, File(dir, "cache"), Settings(node))
        val player = MpvPlayer()
        player.property("mute", "yes")
        val session = PlayerSession(app.settings, player, video)
        try {
            Fixture(app, session).block()
        } finally {
            session.close()
            player.destroy()
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }

    /** Polls [condition], since playback moves on mpv's own threads. */
    private fun waitFor(what: String, timeoutMillis: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for $what")
    }

    @Test
    fun playsLinesAndLooksUpWords() {
        assumeTrue("set IMMERSION_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..4.0 to "今日は何を食べたい？", 5.0..8.0 to "食べさせられなかった。", 9.0..12.0 to "勉強しましょう。"),
            en = srt(1.0..4.0 to "What do you want to eat today?", 5.0..8.0 to "I couldn't make them eat.", 9.0..12.0 to "Let's study."),
        ) {
            YomitanImporter(app.database).import("test.zip", {}) { dictionaryZip().inputStream() }
            val keys = PlayerKeys()
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Midnight) {
                        PlayerScreen(app, session, keys, onBack = {})
                    }
                }
                // first line plays, and its first kanji word is looked up without a click
                waitUntil(timeoutMillis = 15_000) { shows("今日は何を食べたい") && shows("today") }
                session.pause()
                shot("1-first-line")

                // next line: the inflected verb resolves to its dictionary form
                session.playLine(session.lineIndex.value + 1)
                waitUntil(timeoutMillis = 10_000) { shows("食べさせられなかった") && shows("to eat") }
                session.pause()
                shot("2-deinflected")

                // with Anki cards on, every entry gets a round + to make a card of it
                app.settings.updateAnkiEnabled(true)
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithContentDescription("Add to Anki").fetchSemanticsNodes().isNotEmpty()
                }
                shot("2-anki")
                app.settings.updateAnkiEnabled(false)

                // hold E: the English replaces the Japanese
                keys.peeking = true
                waitUntil(timeoutMillis = 5_000) { shows("I couldn't make them eat.") }
                mainClock.advanceTimeBy(500)
                shot("3-english")
                keys.peeking = false

                session.playLine(session.lineIndex.value + 1)
                waitUntil(timeoutMillis = 10_000) { shows("勉強しましょう") && shows("study") }

                // rounded cards, and the video cropped to fill its card
                app.settings.updateRoundedCorners(true)
                app.settings.updateVideoFill(true)
                session.pause()
                Thread.sleep(600)
                mainClock.advanceTimeBy(500)
                shot("4-rounded-fill")
            }
        }
    }

    /** Forward from a pause plays on; forward again, still before the next line, jumps to it. */
    @Test
    fun forwardCarriesOnBeforeItSkipsAhead() {
        assumeTrue("set IMMERSION_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..3.0 to "一つ目の行。", 6.0..8.0 to "二つ目の行。"),
            en = srt(1.0..3.0 to "The first line.", 6.0..8.0 to "The second line."),
        ) {
            session.setTracks(app.loadSubtitles(session.video))
            session.playLine(0, stopAtEnd = true)
            waitFor("the first line to stop at its end") { session.paused.value }
            val stopped = session.position.value
            assertTrue("stopped at the end of the first line, not $stopped", stopped in 2.5..4.0)

            // the first press carries on from there rather than jumping to the second line
            session.nextLine()
            waitFor("playback to carry on") { session.position.value > stopped + 0.3 }
            assertFalse(session.paused.value)
            assertTrue("still before the second line, not at ${session.position.value}", session.position.value < 6.0)

            // pressing again, while it's still waiting for that line, jumps to it
            session.nextLine()
            waitFor("the second line") { session.lineActive.value }
            assertEquals(1, session.lineIndex.value)
        }
    }

    /** With "stop at the end of every line" on, j/k stop there and h/l play on regardless. */
    @Test
    fun plainKeysPlayOnThroughTheStopAtEndSetting() {
        assumeTrue("set IMMERSION_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..2.0 to "一つ目の行。", 3.0..4.0 to "二つ目の行。", 5.0..6.0 to "三つ目の行。"),
            en = srt(1.0..2.0 to "The first line.", 3.0..4.0 to "The second line.", 5.0..6.0 to "The third line."),
        ) {
            app.settings.updateAutoPause(true)
            session.setTracks(app.loadSubtitles(session.video))

            // the setting stops playback at the end of the line it plays
            session.playLine(0, stopAtEnd = true)
            waitFor("the first line to stop at its end") { session.paused.value }

            // k: on into the second line, and stopped at the end of that one
            session.nextLine(stopAtEnd = true)
            waitFor("the second line to stop at its end") { session.paused.value && session.lineIndex.value == 1 }
            assertTrue("stopped at the end of the second line, not at ${session.position.value}", session.position.value in 3.5..4.5)

            // l: on through the third line's end, because the plain keys don't stop
            session.nextLine()
            waitFor("playback past the end of the third line") { session.position.value > 6.4 }
            assertFalse("kept playing past the third line", session.paused.value)
        }
    }
}
