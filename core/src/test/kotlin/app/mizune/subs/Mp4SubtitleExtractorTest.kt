package app.mizune.subs

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
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

    /**
     * Layouts ffmpeg doesn't write, built box by box: a fragment whose runs say nothing of where
     * their data is (each follows the last, the text's after the video's), a QuickTime track
     * name padded after its length byte, and a run claiming far more lines than it holds.
     */
    @Test
    fun readsRunsWithoutOffsets() {
        val lines = listOf("こんにちは", "元気ですか？").map { text ->
            val bytes = text.toByteArray()
            u16(bytes.size) + bytes
        }
        val moov = box(
            "moov",
            box("mvhd", u32(0, 0, 0, 1000, 0)),
            track(1, "vide", "avc1", u32(0, 0, 0) + "VideoHandler".toByteArray() + byteArrayOf(0)),
            // "Core Media Text" with its length byte, then padding
            track(2, "sbtl", "tx3g", u32(0, 0, 0) + byteArrayOf(15) + "Core Media Text".toByteArray() + ByteArray(3)),
            box("mvex", box("trex", u32(0, 1, 1, 0, 0, 0)), box("trex", u32(0, 2, 1, 0, 0, 0))),
        )
        val ftyp = box("ftyp", "isom".toByteArray() + u32(0) + "isom".toByteArray())
        fun moof(dataStart: Long, claimed: Long) = box(
            "moof",
            box("mfhd", u32(0, 1)),
            // the video: three 10-byte frames from the start of mdat's data, no offset in the run
            box("traf", box("tfhd", u32(0x11, 1) + u64(dataStart) + u32(10)), box("trun", u32(0, 3))),
            // the text: no base and no offset, so straight after the video; a second in, 1.5 s a line
            box(
                "traf",
                box("tfhd", u32(0x8, 2, 1500)),
                box("tfdt", u32(0, 1000)),
                box("trun", u32(0x200, claimed) + u32(*lines.map { it.size.toLong() }.toLongArray())),
            ),
        )
        val dataStart = (ftyp.size + moov.size + moof(0, 0).size + 8).toLong()
        val mdat = box("mdat", ByteArray(30) + lines.reduce { a, b -> a + b })

        for (claimed in listOf(2L, 0xFFFFFFFFL)) {
            val file = File.createTempFile("runs", ".mp4").apply { deleteOnExit() }
            file.writeBytes(ftyp + moov + moof(dataStart, claimed) + mdat)
            val tracks = RandomAccessFile(file, "r").use { Mp4SubtitleExtractor(it.channel).extract() }
            assertEquals(listOf("Track 2"), tracks.map { it.name })
            assertEquals("jpn", tracks[0].language)
            assertCues(listOf(Cue(1.0, 2.5, "こんにちは"), Cue(2.5, 4.0, "元気ですか？")), tracks[0].cues)
        }
    }

    private fun track(id: Long, handler: String, entry: String, handlerName: ByteArray) = box(
        "trak",
        box("tkhd", u32(0, 0, 0, id)),
        box(
            "mdia",
            // language: "jpn" packed five bits a letter
            box("mdhd", u32(0, 0, 0, 1000, 0) + u16(('j' - '`' shl 10) or ('p' - '`' shl 5) or ('n' - '`')) + u16(0)),
            box("hdlr", u32(0, 0) + handler.toByteArray() + handlerName),
            box(
                "minf",
                box(
                    "stbl",
                    box("stsd", u32(0, 1, 16) + entry.toByteArray() + ByteArray(8)),
                    box("stts", u32(0, 0)),
                    box("stsc", u32(0, 0)),
                    box("stsz", u32(0, 0, 0)),
                    box("stco", u32(0, 0)),
                ),
            ),
        ),
    )

    private fun box(type: String, vararg content: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply { content.forEach(::write) }.toByteArray()
        return u32(8L + body.size) + type.toByteArray() + body
    }

    private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun u32(vararg values: Long) = values.fold(ByteArray(0)) { out, v ->
        out + byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    }

    private fun u64(value: Long) = u32(value ushr 32, value and 0xFFFFFFFFL)

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
