package io.github.immersionplayer.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import io.github.immersionplayer.subs.ImageSubtitles
import io.github.immersionplayer.subs.translationFor
import io.github.immersionplayer.ui.AppTheme
import io.github.immersionplayer.subs.MatroskaSubtitleExtractor
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

/**
 * Reads the image (PGS) subtitles of a real video with the bundled Tesseract models, a line at a
 * time as the player would. Needs an MKV with PGS tracks in IMMERSION_TEST_PGS; skips itself otherwise.
 */
class ImageSubtitleTest {
    private val video = System.getenv("IMMERSION_TEST_PGS")?.let(::File)

    @Test
    fun readsImageSubtitles() {
        assumeTrue("set IMMERSION_TEST_PGS", video?.isFile == true)
        val scanStarted = System.nanoTime()
        val (text, images) = RandomAccessFile(video, "r").use { MatroskaSubtitleExtractor(it.channel).extractAll() }
        println("scanned in ${(System.nanoTime() - scanStarted) / 1_000_000} ms")
        println("text tracks: ${text.map { "${it.name} [${it.language}] ${it.cues.size}" }}")
        println("image tracks: ${images.map { "${it.name} [${it.language}] ${it.lineCount} lines" }}")
        assumeTrue("the video has image subtitles", images.isNotEmpty())

        val dataDir = TesseractRecognizer.dataDir(Files.createTempDirectory("tessdata").toFile(), listOf("eng", "jpn"))
        for (image in images) {
            val model = ImageSubtitles.model(image) ?: continue
            val track = ImageSubtitles.open(image, { TesseractRecognizer(dataDir, model) }, store = null)
            val lines = track.pictures!!
            val middle = image.lineCount / 2
            val firstRead = CountDownLatch(1)
            val allRead = CountDownLatch(image.lineCount)
            var firstIndex = -1
            lines.onRead = { if (firstIndex < 0) { firstIndex = it; firstRead.countDown() }; allRead.countDown() }
            val started = System.nanoTime()
            lines.want(middle) // as if the viewer had jumped there
            lines.start()
            assertTrue(firstRead.await(10, TimeUnit.SECONDS))
            val firstMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(allRead.await(120, TimeUnit.SECONDS))
            val allMs = (System.nanoTime() - started) / 1_000_000
            lines.close()
            println("\n${track.name}: line $firstIndex (wanted $middle) read in $firstMs ms; all ${image.lineCount} in $allMs ms")
            track.cues.drop(middle).take(12).forEach { println("  %7.2f–%7.2f  %s".format(it.start, it.end, it.text.replace("\n", " / "))) }
            assertTrue("read the wanted line first", firstIndex == middle)
            assertTrue("read most lines", track.cues.count { it.text.isNotEmpty() } > image.lineCount * 0.9)
        }
    }

    /** In the player: jump into the video, hold peek, and the English read from pictures shows. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun peeksAtPictureSubtitlesInThePlayer() {
        assumeTrue("set IMMERSION_TEST_PGS", video?.isFile == true)
        val dir = Files.createTempDirectory("immersion-pgs").toFile()
        val link = File(dir, "Episode.mkv") // alone in its folder, so no sidecars
        Files.createSymbolicLink(link.toPath(), video!!.absoluteFile.toPath())
        val node = "io/github/immersionplayer-pgs-test"
        Preferences.userRoot().node(node).removeNode()
        val app = DesktopApp(File(dir, "data").apply { mkdirs() }, File(dir, "cache"), Settings(node))
        app.settings.updatePeekLanguage("en")
        val player = MpvPlayer().apply { property("mute", "yes") }
        val session = PlayerSession(app.settings, player, link)
        val keys = PlayerKeys()
        try {
            runDesktopComposeUiTest(width = 1440, height = 860) {
                setContent { DesktopTheme(AppTheme.Midnight) { PlayerScreen(app, session, keys, onBack = {}, onOpenVideo = {}) } }
                waitUntil(timeoutMillis = 20_000) { session.secondary.value?.pictures != null }
                val english = session.secondary.value!!
                val primary = session.primary.value!!
                // a Japanese line in the middle that has English under it
                val line = primary.cues.indices.drop(primary.cues.size / 2).first { i ->
                    val cue = primary.cues[i]
                    english.cues.any { it.start < cue.end - 0.1 && it.end > cue.start + 0.1 }
                }
                session.playLine(line, stopAtEnd = true)
                keys.peeking = true
                waitUntil(timeoutMillis = 10_000) {
                    val translation = english.translationFor(primary.cues[line].start, primary.cues[line].end)
                    translation != null && onAllNodesWithText(translation.lines().first(), substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                val shown = english.translationFor(primary.cues[line].start, primary.cues[line].end)
                println("line $line: ${primary.cues[line].text}  ->  $shown (${english.pictures!!.readCount}/${english.cues.size} read so far)")
            }
        } finally {
            session.close()
            player.destroy()
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
