package app.mizune.desktop

import app.mizune.subs.SubtitleImage
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.nio.file.Files

/**
 * Tesseract loads and reads a line of text drawn here, with the bundled English model. Skips
 * itself where there's no libtesseract, unless MIZUNE_TEST_TESSERACT is set (Windows CI, to
 * prove the bundled DLLs load); MIZUNE_TEST_RESOURCES points it at a packaged build's libraries.
 */
class TesseractTest {
    @Test
    fun readsALineOfText() {
        val loaded = runCatching { TesseractLib.INSTANCE }
        if (System.getenv("MIZUNE_TEST_TESSERACT") == null) assumeTrue("no libtesseract here", loaded.isSuccess)
        loaded.getOrThrow()
        val dataDir = TesseractRecognizer.dataDir(Files.createTempDirectory("tessdata").toFile(), listOf("eng"))
        val text = TesseractRecognizer(dataDir, "eng").use { it.read(picture("Subtitles read by Tesseract")) }
        assertTrue("read \"$text\"", "Tesseract" in text && "Subtitles" in text)
    }

    /** [text] in black on white, the size a subtitle line comes in, as grey pixels. */
    private fun picture(text: String): SubtitleImage {
        val image = BufferedImage(900, 120, BufferedImage.TYPE_BYTE_GRAY)
        image.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, image.width, image.height)
            color = Color.BLACK
            font = Font(Font.SANS_SERIF, Font.PLAIN, 48)
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            drawString(text, 30, 78)
            dispose()
        }
        val pixels = (image.raster.dataBuffer as DataBufferByte).data
        return SubtitleImage(image.width, image.height, pixels)
    }
}
