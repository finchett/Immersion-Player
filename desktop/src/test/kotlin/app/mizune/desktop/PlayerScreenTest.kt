package app.mizune.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.mizune.desktop.mpv.MpvPlayer
import app.mizune.dictionary.YomitanImporter
import app.mizune.ui.AppTheme
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
 * Needs a video (any codec mpv plays) in MIZUNE_TEST_VIDEO; skips itself otherwise.
 * Set MIZUNE_TEST_SHOTS to a directory to save screenshots.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerScreenTest {

    private val videoSource = System.getenv("MIZUNE_TEST_VIDEO")?.let(::File)
    private val shots = System.getenv("MIZUNE_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }

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
        // an open menu is a root of its own, over the window's: the last root is the top one
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    /** The window itself: a tooltip or menu open over it is a root of its own. */
    private fun SemanticsNodeInteractionsProvider.window() = onAllNodes(isRoot()).onFirst()

    private fun SemanticsNodeInteractionsProvider.shows(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** A temp library holding one video and its subtitles, with a player and session on it. */
    private class Fixture(val app: DesktopApp, val session: PlayerSession)

    private val node = "app/mizune-test"

    /** Builds that library, runs [block] against it, and takes it all down again afterwards. */
    private fun fixture(ja: String, en: String, block: Fixture.() -> Unit) {
        val dir = Files.createTempDirectory("mizune-test").toFile()
        val video = File(dir, "Lesson 01.mkv")
        Files.createSymbolicLink(video.toPath(), videoSource!!.absoluteFile.toPath())
        File(dir, "Lesson 01.ja.srt").writeText(ja)
        File(dir, "Lesson 01.en.srt").writeText(en)
        // the episode after it, for the end-of-video prompt
        Files.createSymbolicLink(File(dir, "Lesson 02.mkv").toPath(), videoSource.absoluteFile.toPath())

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
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..4.0 to "今日は何を食べたい？", 5.0..8.0 to "食べさせられなかった。", 9.0..12.0 to "勉強しましょう。"),
            en = srt(1.0..4.0 to "What do you want to eat today?", 5.0..8.0 to "I couldn't make them eat.", 9.0..12.0 to "Let's study."),
        ) {
            YomitanImporter(app.database).import("test.zip", {}) { dictionaryZip().inputStream() }
            val keys = PlayerKeys()
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Jade) {
                        PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {})
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

    /** At the end of the video the next one in the folder is offered, by click or Return. */
    @Test
    fun offersTheNextEpisodeAtTheEnd() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(ja = srt(1.0..2.0 to "一つ目の行。"), en = srt(1.0..2.0 to "The first line.")) {
            val keys = PlayerKeys()
            var opened: File? = null
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Jade) {
                        PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = { opened = it })
                    }
                }
                waitUntil(timeoutMillis = 15_000) { session.duration.value > 0 }
                assertFalse(shows("Next episode"))
                assertEquals(null, keys.onPlayNext)

                session.seekTo(session.duration.value - 0.5)
                waitUntil(timeoutMillis = 15_000) { shows("Next episode") && shows("Lesson 02") }
                shot("5-next-episode")
                keys.onPlayNext!!()
                assertEquals("Lesson 02.mkv", opened?.name)

                // seeking back into the video takes the offer away again
                opened = null
                session.seekTo(1.0)
                waitUntil(timeoutMillis = 15_000) { !shows("Next episode") }
                assertEquals(null, keys.onPlayNext)
            }
        }
    }

    /**
     * The shader button on the video's controls turns Anime4K on at a preset, in the mode the
     * video's resolution suggests; Advanced picks a mode instead. mpv runs what's chosen.
     */
    @Test
    fun shaderMenuSetsAnime4K() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(ja = srt(1.0..2.0 to "一つ目の行。"), en = srt(1.0..2.0 to "The first line.")) {
            assumeTrue("needs OpenGL", session.player.runsShaders)
            val keys = PlayerKeys()
            fun shaders() = session.player.propertyString("glsl-shaders").orEmpty()
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Jade) {
                        PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {})
                    }
                }
                waitUntil(timeoutMillis = 15_000) { session.player.videoHeight.value > 0 }
                val byVideo = Anime4K.modeFor(session.player.videoHeight.value)
                // paused, the controls stay up
                session.pause()
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Shaders").fetchSemanticsNodes().isNotEmpty() }

                // Fast: the mode for this video, lighter networks
                onNodeWithContentDescription("Shaders").performClick()
                waitUntil(timeoutMillis = 5_000) { shows("Best") }
                assertFalse("modes start folded away", shows("Mode B"))
                // Auto says which of Fast and Best it went with
                val autoPick = if (Anime4K.fast(Anime4K.Preset.AUTO, Anime4K.hardware)) "Fast" else "Best"
                assertTrue(shows("Auto · $autoPick"))
                shot("6-shader-menu")
                onNodeWithText("Fast").performClick()
                assertEquals(Anime4K.Preset.FAST, app.settings.anime4kPreset)
                val expected = Anime4K.shaders(byVideo, fast = true).map { it.name }
                waitUntil(timeoutMillis = 5_000) { expected.all { it in shaders() } }

                // Advanced: Mode B instead of following the video
                onNodeWithContentDescription("Shaders").performClick()
                onNodeWithText("Advanced").performClick()
                waitUntil(timeoutMillis = 5_000) { shows("Mode B") && shows("By video") }
                shot("6-shader-advanced")
                onNodeWithText("Mode B").performClick()
                assertEquals(Anime4K.Mode.B, app.settings.anime4kMode)
                waitUntil(timeoutMillis = 5_000) { "Anime4K_Restore_CNN_Soft_M.glsl" in shaders() }

                // Off clears them
                onNodeWithContentDescription("Shaders").performClick()
                onNodeWithText("Off").performClick()
                waitUntil(timeoutMillis = 5_000) { shaders().isEmpty() }
            }
        }
    }

    /** Forward from a pause plays on; forward again, still before the next line, jumps to it. */
    @Test
    fun forwardCarriesOnBeforeItSkipsAhead() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
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

    /**
     * Audio follows the target language rather than the file's default track, and a track picked by
     * hand wins when the video is opened again. Needs a test video with Japanese and other audio.
     */
    @Test
    fun audioFollowsTheTargetLanguage() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(ja = srt(1.0..3.0 to "一つ目の行。"), en = srt(1.0..3.0 to "The first line.")) {
            waitFor("the audio tracks") { session.audioTracks.value.isNotEmpty() }
            val tracks = session.audioTracks.value
            val japanese = tracks.firstOrNull { it.language == "jpn" || it.language == "ja" }
            val other = tracks.firstOrNull { it != japanese }
            assumeTrue("the test video needs Japanese and other audio", japanese != null && other != null)
            waitFor("Japanese audio") { session.audioTrack.value == japanese!!.id }

            session.selectAudio(other!!)
            assertEquals(other.id, session.player.propertyString("aid")?.toIntOrNull())
            session.close()
            val again = PlayerSession(app.settings, session.player, session.video)
            try {
                waitFor("the picked audio after reopening") { again.audioTrack.value == other.id }
            } finally {
                again.close()
            }
        }
    }

    /** A track picked by hand beats the automatic choice, and is still picked when the video's tracks load again. */
    @Test
    fun pickedTracksAreRemembered() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..3.0 to "一つ目の行。"),
            en = srt(1.0..3.0 to "The first line."),
        ) {
            val tracks = app.loadSubtitles(session.video)
            session.setTracks(tracks)
            val japanese = session.primary.value!!
            val english = session.secondary.value!!
            assertEquals("ja", japanese.language)
            assertEquals("en", english.language)

            session.selectPrimary(english)
            session.selectSecondary(null)
            session.setTracks(app.loadSubtitles(session.video))
            assertEquals(english.name, session.primary.value?.name)
            assertEquals(null, session.secondary.value)
        }
    }

    /** Back from a line's stop replays that line; back again, now it plays, goes to the line before. */
    @Test
    fun backFromAStopReplaysTheLineFirst() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..3.0 to "一つ目の行。", 5.0..7.0 to "二つ目の行。"),
            en = srt(1.0..3.0 to "The first line.", 5.0..7.0 to "The second line."),
        ) {
            session.setTracks(app.loadSubtitles(session.video))
            session.playLine(1, stopAtEnd = true)
            waitFor("the second line to stop at its end") { session.paused.value && session.position.value > 6.5 }

            session.previousLine(stopAtEnd = true)
            waitFor("the second line again") { !session.paused.value && session.lineIndex.value == 1 }
            assertTrue("replayed from its start, not ${session.position.value}", session.position.value < 5.6)

            session.previousLine(stopAtEnd = true)
            waitFor("the first line") { session.lineIndex.value == 0 && session.lineActive.value }
        }
    }

    /** With "stop at the end of every line" on, j/k stop there and h/l play on regardless. */
    @Test
    fun plainKeysPlayOnThroughTheStopAtEndSetting() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
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

    /**
     * The stop button under the panel shows whether lines stop, a stop leaves the controls hidden,
     * and with the panel put away the line goes over the video, where pointing at a word looks it up.
     */
    @Test
    fun panelControlsAndTheLineOverTheVideo() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        fixture(
            ja = srt(1.0..3.0 to "今日は何を食べたい？", 4.0..6.0 to "勉強しましょう。"),
            en = srt(1.0..3.0 to "What do you want to eat today?", 4.0..6.0 to "Let's study."),
        ) {
            YomitanImporter(app.database).import("test.zip", {}) { dictionaryZip().inputStream() }
            val keys = PlayerKeys()
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Jade) {
                        PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {})
                    }
                }
                fun stopButton(hint: String) = onAllNodesWithContentDescription(hint).fetchSemanticsNodes().isNotEmpty()
                waitUntil(timeoutMillis = 15_000) { shows("今日は何を食べたい") && stopButton("Stop at the end of each line") }

                // the stop button turns stops on, and its hint says so
                onAllNodesWithContentDescription("Stop at the end of each line").onFirst().performClick()
                waitUntil(timeoutMillis = 5_000) { stopButton("Stopping at the end of each line") }
                assertTrue(app.settings.autoPause)

                // the stop at the line's end leaves the controls hidden; a pause asked for shows them
                waitUntil(timeoutMillis = 10_000) { session.stoppedAtLine.value }
                waitForIdle()
                assertFalse("controls stayed hidden at the stop", keys.chromeVisible)
                shot("5-stopped-at-line")

                // l plays on through the stops, and the button shows that too
                session.nextLine()
                waitUntil(timeoutMillis = 5_000) { stopButton("Stops held off by H or L: click to stop again") }
                session.pause()
                waitUntil(timeoutMillis = 5_000) { keys.chromeVisible }
                shot("6-held-off")

                // the panel put away: the line over the video, and pointing at a word looks it up there
                session.playLine(0, stopAtEnd = true)
                waitUntil(timeoutMillis = 10_000) { session.stoppedAtLine.value }
                app.settings.updatePanelHidden(true)
                waitUntil(timeoutMillis = 5_000) { !shows("today") && shows("今日は何を食べたい") }
                mainClock.advanceTimeBy(500)
                shot("7-line-over-video")
                fun has(description: String) = onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
                fun anyControls() = keys.chromeVisible || shows("‹ Library") || has("Bring the panel back") || shows(" / ")

                // the pointer on the line, and a pause: no controls, and the line stays put
                fun lineTop() = onNodeWithText("今日は何を食べたい？").fetchSemanticsNode().boundsInRoot.top
                val before = lineTop()
                onNodeWithText("今日は何を食べたい？").performMouseInput { moveTo(center) }
                waitUntil(timeoutMillis = 5_000) { shows("to eat") }
                session.togglePause()
                session.togglePause()
                mainClock.advanceTimeBy(1_000)
                assertFalse("no controls for the line", anyControls())
                assertEquals("the line moved", before, lineTop(), 0.5f)
                assertTrue("Esc closes the lookup", keys.onDismiss != null)
                shot("8-lookup-over-video")

                // straight up across the gap into the entries, they stay; off them, they go at once
                val line = onNodeWithText("今日は何を食べたい？")
                line.performMouseInput { moveTo(Offset(center.x, -6f)) }
                line.performMouseInput { moveTo(Offset(center.x, -40f)) }
                mainClock.advanceTimeBy(1_000)
                assertTrue("the entries stayed while pointed at", shows("to eat"))
                mainClock.autoAdvance = false
                line.performMouseInput { moveTo(Offset(center.x, -400f)) }
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertFalse("the entries went at once", shows("to eat"))
                mainClock.autoAdvance = true

                // moving about the quiet middle, beside the line and just inside the side bands: still nothing
                // (as fractions of the video's width and height)
                for ((x, y) in listOf(0.33f to 0.33f, 0.15f to 0.5f, 0.25f to 0.9f)) {
                    window().performMouseInput { moveTo(Offset(width * x, height * y)) }
                }
                mainClock.advanceTimeBy(500)
                assertFalse("no controls in the quiet area", anyControls())

                // the strip under the line brings everything up, the line clear of the bar; it all stays
                // as the pointer goes along the bar, and goes once it's back in the quiet area
                fun allControls() = keys.chromeVisible && shows("‹ Library") && has("Bring the panel back") && shows(" / ")
                val low = lineTop()
                window().performMouseInput { moveTo(Offset(width - 40f, height - 4f)) }
                waitUntil(timeoutMillis = 5_000) { allControls() }
                window().performMouseInput { moveTo(Offset(width - 200f, height - 30f)) }
                mainClock.advanceTimeBy(1_000)
                assertTrue("the controls stayed by the bar", allControls())
                val lineBottom = onNodeWithText("今日は何を食べたい？").fetchSemanticsNode().boundsInRoot.bottom
                val windowHeight = window().fetchSemanticsNode().boundsInRoot.height
                assertTrue("the line clear of the bar, at $lineBottom", lineBottom < windowHeight - 40f)
                shot("9-from-corner")
                window().performMouseInput { moveTo(Offset(width / 3f, height / 3f)) }
                waitUntil(timeoutMillis = 5_000) { !anyControls() }
                mainClock.advanceTimeBy(1_000)
                assertEquals("the line went back down", low, lineTop(), 0.5f)

                // anywhere along it, and down the sides, likewise
                for ((x, y) in listOf(0.5f to 0.995f, 0.01f to 0.5f, 0.99f to 0.3f)) {
                    window().performMouseInput { moveTo(Offset(width * x, height * y)) }
                    waitUntil(timeoutMillis = 5_000) { allControls() }
                    window().performMouseInput { moveTo(Offset(width / 3f, height / 3f)) }
                    waitUntil(timeoutMillis = 5_000) { !anyControls() }
                }

                // and the band along the top
                window().performMouseInput { moveTo(Offset(width / 2f, height * 0.2f)) }
                waitUntil(timeoutMillis = 5_000) { allControls() }
                mainClock.advanceTimeBy(500)
                val library = onNodeWithText("‹ Library").fetchSemanticsNode().boundsInRoot
                assertTrue("Library top right, not at $library", library.right > 1300f && library.top < 100f)
                shot("10-from-top")
                window().performMouseInput { moveTo(Offset(width / 2f, height / 2f)) }
                waitUntil(timeoutMillis = 5_000) { !anyControls() }

                // the panel button brings the panel back
                window().performMouseInput { moveTo(Offset(width - 40f, height - 4f)) }
                waitUntil(timeoutMillis = 5_000) { has("Bring the panel back") }
                onNodeWithContentDescription("Bring the panel back").performClick()
                waitUntil(timeoutMillis = 5_000) { !app.settings.panelHidden }
                waitUntil(timeoutMillis = 5_000) { stopButton("Stopping at the end of each line") }
            }
        }
    }

    /** A short line with a long translation: peeking shows all of the English, in the panel and over the video. */
    @Test
    fun peekFitsALongTranslation() {
        assumeTrue("set MIZUNE_TEST_VIDEO", videoSource?.isFile == true)
        val english = "Yes, of course I remember it, it was the summer we spent at my grandmother's house by the sea, " +
            "the one with the blue door and the garden full of tomatoes that nobody ever managed to finish eating."
        fixture(ja = srt(1.0..4.0 to "はい。"), en = srt(1.0..4.0 to english)) {
            val keys = PlayerKeys()
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent {
                    DesktopTheme(AppTheme.Jade) {
                        PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {})
                    }
                }
                waitUntil(timeoutMillis = 15_000) { shows("はい") }
                session.pause()
                fun fits(): Boolean {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithText(english).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    return layouts.single().let { !it.hasVisualOverflow && it.layoutInput.text.text == english }
                }
                keys.peeking = true
                waitUntil(timeoutMillis = 5_000) { shows(english) }
                mainClock.advanceTimeBy(500)
                assertTrue("the English fits the panel", fits())
                shot("12-peek-panel")

                app.settings.updatePanelHidden(true)
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(english).fetchSemanticsNodes().size == 1 }
                mainClock.advanceTimeBy(500)
                assertTrue("the English fits over the video", fits())
                shot("13-peek-over-video")
                keys.peeking = false
            }
        }
    }
}
