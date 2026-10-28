package io.github.immersionplayer.desktop

import io.github.immersionplayer.desktop.mpv.MpvPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The player's GL path checks its frames and drops to software when they come back empty. Needs
 * a video with something to see 2 minutes in (IMMERSION_TEST_VIDEO); skips itself otherwise.
 */
class GlFallbackTest {
    private val video = System.getenv("IMMERSION_TEST_VIDEO")?.let(::File)

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for $what")
    }

    /** Plays [video] from 2 minutes in, muted, and returns the player once frames are coming. */
    private fun playing(): MpvPlayer {
        val player = MpvPlayer()
        player.property("mute", "yes")
        player.setTargetSize(640, 360)
        player.load(video!!.path)
        player.setPaused(false)
        waitFor("the video to load") { player.duration.value > 0 }
        player.seek(120.0)
        waitFor("playback past 2 minutes") { player.position.value > 121 }
        return player
    }

    private fun frameBytes(player: MpvPlayer): ByteArray {
        val bitmap = player.frame.value!!.bitmap
        return bitmap.readPixels(bitmap.imageInfo, bitmap.rowBytes, 0, 0)!!
    }

    @Test
    fun glFramesAreOpaque() {
        assumeTrue("set IMMERSION_TEST_VIDEO", video?.isFile == true)
        val player = playing()
        try {
            assumeTrue("needs a GPU to render with", player.runsShaders)
            // BGRA: every fourth byte is alpha, which mpv leaves fully opaque
            val bytes = frameBytes(player)
            val alphas = (3 until bytes.size step 4 * 997).map { bytes[it] }
            assertTrue("alpha ${alphas.distinct()}", alphas.all { it == 0xFF.toByte() })
        } finally {
            player.destroy()
        }
    }

    @Test
    fun blankGlFramesFallBackToSoftware() {
        assumeTrue("set IMMERSION_TEST_VIDEO", video?.isFile == true)
        System.setProperty("immersion.blankGl", "1")
        val player = try { playing() } finally { System.clearProperty("immersion.blankGl") }
        try {
            waitFor("the switch to software") { !player.runsShaders }
            val first = player.frame.value?.serial ?: 0
            waitFor("a frame from the software renderer") { (player.frame.value?.serial ?: 0) > first + 2 }
            assertFalse(player.runsShaders)
            // and it shows the picture: not every byte zero
            assertTrue(frameBytes(player).any { it != 0.toByte() })
        } finally {
            player.destroy()
        }
    }
}
