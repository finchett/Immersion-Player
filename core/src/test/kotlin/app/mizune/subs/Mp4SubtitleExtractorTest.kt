package app.mizune.subs

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Small files made by ffmpeg from a two-frames-a-second black video and a few lines of SRT, e.g.
 * `ffmpeg -f lavfi -i color=c=black:s=64x64:r=2:d=12 -i ja.srt -i en.srt -map 0 -map 1 -map 2
 * -c:v libx264 -c:s mov_text -metadata:s:s:0 language=jpn ... plain.mp4`. The expected times are
 * the ones ffprobe reads from them.
 */
class Mp4SubtitleExtractorTest {
    private fun extract(name: String): List<SubtitleTrack> {
        val file = File(javaClass.getResource("/mp4/$name")!!.toURI())
        return RandomAccessFile(file, "r").use { SubtitleFiles.extractEmbedded(name, it.channel) }
    }

    private fun assertCues(expected: List<Cue>, actual: List<Cue>) {
        assertEquals(expected.map { it.text }, actual.map { it.text })
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.start, a.start, 0.001)
            assertEquals(e.end, a.end, 0.001)
        }
    }

    private val japanese = listOf(
        Cue(1.0, 2.5, "こんにちは"),
        Cue(3.0, 5.25, "元気ですか？\n二行目"),
        Cue(7.1, 9.0, "さようなら"),
    )

    @Test
    fun readsTimedTextTracks() {
        val tracks = extract("plain.mp4")
        assertEquals(listOf("Japanese (Full)", "English Signs"), tracks.map { it.name })
        assertEquals(listOf("jpn", "eng"), tracks.map { it.language })
        assertCues(japanese, tracks[0].cues)
        // the English went in half a second late
        assertCues(listOf(Cue(1.5, 3.0, "Hello"), Cue(3.5, 5.75, "How are you?")), tracks[1].cues)
        assertEquals(tracks[0], SubtitleFiles.pickPrimary(tracks))
    }

    @Test
    fun appliesTheEditList() {
        // plain.mp4 with the Japanese track's edit list changed to start playing it a second in
        val cues = extract("edit-list.mp4").first().cues
        assertCues(japanese.map { it.copy(start = it.start - 1, end = it.end - 1) }, cues)
    }

    @Test
    fun readsFragments() {
        // -movflags frag_keyframe+empty_moov -g 6: a fragment every three seconds. ffmpeg stretches
        // the last line of a fragment to the next one, so some times differ from the SRT's.
        val tracks = extract("fragmented.mp4")
        assertEquals(listOf("jpn", "eng"), tracks.map { it.language })
        assertCues(
            listOf(Cue(1.0, 3.0, "こんにちは"), Cue(3.0, 7.1, "元気ですか？\n二行目"), Cue(7.1, 9.0, "さようなら")),
            tracks[0].cues,
        )
        assertCues(listOf(Cue(1.0, 3.0, "Hello"), Cue(3.0, 5.25, "How are you?")), tracks[1].cues)
    }

    /** A real file's first text track against ffmpeg's reading of it. Set MIZUNE_TEST_MP4 and MIZUNE_TEST_MP4_SRT to run it. */
    @Test
    fun matchesFfmpeg() {
        val mp4 = System.getenv("MIZUNE_TEST_MP4")?.let(::File)?.takeIf { it.exists() }
        val srt = System.getenv("MIZUNE_TEST_MP4_SRT")?.let(::File)?.takeIf { it.exists() }
        assumeTrue("sample files not configured", mp4 != null && srt != null)

        val started = System.nanoTime()
        val tracks = RandomAccessFile(mp4, "r").use { Mp4SubtitleExtractor(it.channel).extract() }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("extracted ${tracks.size} tracks in ${millis}ms: " + tracks.joinToString { "${it.name} [${it.language}] ${it.cues.size}" })
        assertCues(SrtParser.parse(srt!!.readText()), tracks.first().cues)
    }

    @Test
    fun skipsQuickTimeChapters() {
        // a .mov with two chapters, which QuickTime keeps as a text track of their titles
        val tracks = extract("chapters.mov")
        assertEquals(1, tracks.size)
        assertEquals("Japanese (Full)", tracks[0].name)
        // a Macintosh language code, as ffmpeg writes in QuickTime files
        assertEquals("jpn", tracks[0].language)
        assertCues(japanese, tracks[0].cues)
    }
}
