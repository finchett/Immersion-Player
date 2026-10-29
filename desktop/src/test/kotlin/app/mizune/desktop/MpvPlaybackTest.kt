package app.mizune.desktop

import app.mizune.desktop.mpv.MpvLib
import app.mizune.desktop.mpv.MpvPlayer
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/**
 * libmpv loads and plays a short test pattern through the player, and the picture isn't blank;
 * prints which mpv and FFmpeg it is and how mpv was configured (where a build without GPL code
 * shows). Skips itself where there's no libmpv, unless MIZUNE_TEST_MPV is set (Windows CI, for
 * the bundled one); MIZUNE_TEST_RESOURCES points it at a packaged build's libraries.
 */
class MpvPlaybackTest {
    @Test
    fun playsATestPattern() {
        val loaded = runCatching { MpvLib.INSTANCE }
        if (System.getenv("MIZUNE_TEST_MPV") == null) assumeTrue("no libmpv here", loaded.isSuccess)
        loaded.getOrThrow()
        val video = Files.createTempFile("testsrc", ".mkv").toFile().apply { deleteOnExit() }
        javaClass.getResourceAsStream("/testsrc.mkv")!!.use { input -> video.outputStream().use { input.copyTo(it) } }

        val player = MpvPlayer()
        try {
            player.property("mute", "yes")
            player.setTargetSize(320, 180)
            player.load(video.path)
            player.setPaused(false)
            val deadline = System.currentTimeMillis() + 20_000
            while ((player.frame.value?.serial ?: 0) < 5 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            println("mpv: ${player.propertyString("mpv-version")}")
            println("ffmpeg: ${player.propertyString("ffmpeg-version")}")
            println("configuration: ${player.propertyString("mpv-configuration")}")
            val frame = player.frame.value
            assertTrue("frames came", frame != null && frame.serial >= 5)
            val bitmap = frame!!.bitmap
            val bytes = bitmap.readPixels(bitmap.imageInfo, bitmap.rowBytes, 0, 0)!!
            // the pattern is mostly bright colour: well over a tenth of the bytes aren't zero
            assertTrue("the picture is blank", bytes.count { it != 0.toByte() } > bytes.size / 10)
        } finally {
            player.destroy()
        }
    }
}
