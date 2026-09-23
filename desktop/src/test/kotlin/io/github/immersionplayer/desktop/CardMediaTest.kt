package io.github.immersionplayer.desktop

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Cuts a clip and a frame with the real libmpv. Needs a video in IMMERSION_TEST_VIDEO. */
class CardMediaTest {
    private val video = System.getenv("IMMERSION_TEST_VIDEO")?.let(::File)?.takeIf { it.isFile }
    private val dir = Files.createTempDirectory("card-media").toFile()

    @Test fun audioClipIsOpus() {
        assumeTrue(video != null)
        val out = File(dir, "clip.ogg")
        assertTrue(CardMedia.audioClip(video!!, 60.0, 62.5, null, out))
        // an Ogg page starts every Ogg file; the Opus header follows in the first page
        val head = out.readBytes().take(64).toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(head, head.startsWith("OggS") && "OpusHead" in head)
        assertTrue("${out.length()} bytes", out.length() in 2_000..40_000)
    }

    @Test fun screenshotIsSmallAvif() {
        assumeTrue(video != null)
        val out = File(dir, "frame.avif")
        assertTrue(CardMedia.screenshot(video!!, 61.0, 360, out))
        val head = out.readBytes().take(32).toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(head, "ftypavif" in head)
        assertTrue("${out.length()} bytes", out.length() in 1_000..60_000)
    }

    @Test fun animationIsAnimatedAvif() {
        assumeTrue(video != null)
        val out = File(dir, "line.avif")
        assertTrue(CardMedia.animation(video!!, 60.0, 62.0, 360, out))
        // "avis" is the brand of an AVIF image sequence, which plays; "avif" alone is a still
        val head = out.readBytes().take(32).toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(head, "ftypavis" in head)
        assertTrue("${out.length()} bytes", out.length() in 2_000..200_000)
    }
}
