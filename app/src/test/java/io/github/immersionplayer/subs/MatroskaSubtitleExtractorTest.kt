package io.github.immersionplayer.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Compares embedded subtitle extraction against reference files produced by ffmpeg.
 * Set IMMERSION_TEST_MKV, IMMERSION_TEST_JA_SRT and IMMERSION_TEST_EN_ASS to run it.
 */
class MatroskaSubtitleExtractorTest {
    private fun file(env: String): File? = System.getenv(env)?.let(::File)?.takeIf { it.exists() }

    @Test
    fun extractsTracksMatchingFfmpeg() {
        val mkv = file("IMMERSION_TEST_MKV")
        val jaSrt = file("IMMERSION_TEST_JA_SRT")
        val enAss = file("IMMERSION_TEST_EN_ASS")
        assumeTrue("sample files not configured", mkv != null && jaSrt != null && enAss != null)

        val started = System.nanoTime()
        val tracks = RandomAccessFile(mkv, "r").use { MatroskaSubtitleExtractor(it.channel).extract() }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("extracted ${tracks.size} tracks in ${millis}ms: " + tracks.joinToString { "${it.name} [${it.language}] ${it.cues.size}" })

        val japanese = tracks.single { SubtitleLoader.isJapanese(it) }
        val expectedJa = SrtParser.parse(jaSrt!!.readText())
        assertEquals(expectedJa.size, japanese.cues.size)
        expectedJa.zip(japanese.cues).forEach { (expected, actual) ->
            assertEquals(expected.text, actual.text)
            assertEquals(expected.start, actual.start, 0.002)
            assertEquals(expected.end, actual.end, 0.002)
        }

        val english = tracks.first { it.name.contains("Full Subtitles [Doki]") }
        val expectedEn = AssParser.parse(enAss!!.readText())
        assertEquals(expectedEn.map { it.text }, english.cues.map { it.text })

        assertEquals(english, SubtitleLoader.pickSecondary(tracks, japanese))
        assertTrue(tracks.size == 5)
    }
}
