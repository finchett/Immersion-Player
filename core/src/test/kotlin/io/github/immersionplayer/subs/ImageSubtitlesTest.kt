package io.github.immersionplayer.subs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImageSubtitlesTest {

    @Test
    fun cleanFixesTesseractsHabits() {
        assertEquals("I still have to hand in my journal,\nso you go on ahead.",
            ImageSubtitles.clean("| still have to hand in my journal,\n\n so you go on ahead. \n", japanese = false))
        assertEquals("animal costumes I lent you", ImageSubtitles.clean("animal costumes | lent you", japanese = false))
        assertEquals("I'm here", ImageSubtitles.clean("|'m here", japanese = false))
        // spaces between Japanese characters go; ones between Latin words stay
        assertEquals("まだ日誌届けたりしなきゃ\nLet's go", ImageSubtitles.clean("まだ 日 誌 届け たり しな きゃ\nLet's go", japanese = true))
    }

    @Test
    fun decodesRunLengths() {
        // 4×2: [1, 0, 0, 2] then [3×5 run cut to width... ] — one literal, a zero run, a coloured run, line ends
        val rle = byteArrayOf(
            1, 0, 0x02, 2, 0, 0,          // 1, two 0s, 2, end of line
            0, (0x80 or 4).toByte(), 7, 0, 0, // four 7s, end of line
        )
        assertArrayEquals(byteArrayOf(1, 0, 0, 2, 7, 7, 7, 7), PgsDecoder.decodeRle(rle, 4, 2))
    }

    /** A block showing a 2×1 object (one white opaque pixel, one transparent) at (100, 900). */
    private fun show(at: Double, epoch: Boolean = true, alpha: Int = 255, rle: ByteArray = byteArrayOf(1, 0, 0x01, 0, 0)) =
        PgsBlock(at, Double.NaN, segments(
            0x16 to pcs(objects = 1, epoch = epoch),
            0x14 to byteArrayOf(0, 0, 1, 235.toByte(), 128.toByte(), 128.toByte(), alpha.toByte()),
            0x15 to ods(width = 2, height = 1, rle = rle),
            0x80 to byteArrayOf(),
        ))

    private fun clear(at: Double) = PgsBlock(at, Double.NaN, segments(0x16 to pcs(objects = 0), 0x80 to byteArrayOf()))

    @Test
    fun decodesADisplaySetIntoDarkTextOnWhite() {
        val track = ImageSubtitleTrack("t", "eng", listOf(show(10.0), clear(12.5)))
        assertEquals(1, track.lineCount)
        assertEquals(10.0, track.lines[0].start, 0.0)
        assertEquals(12.5, track.lines[0].end, 0.0)

        val image = track.image(0)!!
        assertEquals(2 + 24, image.width)
        assertEquals(1 + 24, image.height)
        val row = 12 * image.width + 12
        assertEquals(0, image.pixels[row].toInt() and 0xFF)   // the white pixel, as black text
        assertEquals(255, image.pixels[row + 1].toInt() and 0xFF) // transparent, as background
        assertEquals(255, image.pixels[0].toInt() and 0xFF)   // margin
    }

    @Test
    fun aFadeIsOneLineButANewPictureIsAnother() {
        val track = ImageSubtitleTrack("t", "eng", listOf(
            show(10.0), show(10.2, alpha = 128), // the same picture fading
            show(11.0, rle = byteArrayOf(2, 2, 0, 0)), // a different line straight after
            clear(13.0),
        ))
        assertEquals(listOf(10.0 to 11.0, 11.0 to 13.0), track.lines.map { it.start to it.end })
    }

    @Test
    fun readsTheWantedLineFirstAndFillsInTheRest() {
        val blocks = (0 until 20).flatMap { i -> listOf(show(i * 2.0, rle = byteArrayOf((i + 1).toByte(), 0, 0)), clear(i * 2.0 + 1)) }
        val track = ImageSubtitleTrack("t", "eng", blocks)
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val gate = java.util.concurrent.CountDownLatch(1)
        val recognizer = object : TextRecognizer {
            var n = 0
            override fun read(image: SubtitleImage): String { gate.await(); return "line ${n++}" }
        }
        val saved = mutableListOf<List<String?>>()
        val store = object : LineStore {
            override fun load() = null
            override fun save(texts: List<String?>) { saved += texts }
        }
        val subtitles = ImageSubtitles.open(track, { recognizer }, store)
        val lines = subtitles.pictures!!
        val done = java.util.concurrent.CountDownLatch(20)
        lines.onRead = { order += it; done.countDown() }
        assertEquals("", subtitles.cues[15].text)

        lines.start()
        // before the first read finishes: 15..19 go next (after line 0, if the reader had already
        // started on it), then the rest wrapping round to the start
        lines.want(15)
        gate.countDown()
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(20, lines.readCount)
        assertEquals(listOf(15, 16, 17, 18, 19), order.filter { it != 0 }.take(5))
        assertEquals((1..14).toList(), order.filter { it in 1..14 })
        assertTrue(subtitles.cues.all { it.text.startsWith("line ") })
        lines.close()
        Thread.sleep(100)
        assertTrue(saved.isNotEmpty() && saved.last().all { it != null })
    }

    @Test
    fun storedTextIsThereAtOnce() {
        val track = ImageSubtitleTrack("t", "eng", listOf(show(1.0), clear(2.0)))
        val store = object : LineStore {
            override fun load() = listOf("Hello")
            override fun save(texts: List<String?>) {}
        }
        val lines = ImageSubtitles.open(track, { error("no OCR needed") }, store)
        assertEquals("Hello", lines.cues[0].text)
        assertTrue(lines.pictures!!.isComplete)
    }

    private fun segments(vararg parts: Pair<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((type, payload) in parts) {
            out.write(type); out.write(payload.size shr 8); out.write(payload.size and 0xFF); out.write(payload)
        }
        return out.toByteArray()
    }

    private fun pcs(objects: Int, epoch: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x07, 0x80.toByte(), 0x04, 0x38, 0x10, 0, 1, if (epoch) 0x80.toByte() else 0, 0, 0, objects.toByte()))
        repeat(objects) { out.write(byteArrayOf(0, 0, 0, 0, 0, 100, 0x03, 0x84.toByte())) } // id 0, window 0, not cropped, (100, 900)
        return out.toByteArray()
    }

    private fun ods(width: Int, height: Int, rle: ByteArray): ByteArray {
        val length = rle.size + 4
        return byteArrayOf(0, 0, 0, 0xC0.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte(),
            (width shr 8).toByte(), width.toByte(), (height shr 8).toByte(), height.toByte()) + rle
    }
}
