package io.github.immersionplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Anime4KTest {
    @Test fun everyModeHasItsShadersBundled() {
        for (mode in Anime4K.Mode.entries) for (fast in listOf(true, false)) {
            val shaders = Anime4K.shaders(mode, fast)
            assertEquals("Anime4K_Clamp_Highlights.glsl", shaders.first().name)
            shaders.forEach { assertTrue("$mode: $it", it.isFile && it.length() > 0) }
        }
    }

    @Test fun modeAMatchesTheMpvInstructions() {
        val hq = listOf("Clamp_Highlights", "Restore_CNN_VL", "Upscale_CNN_x2_VL", "AutoDownscalePre_x2", "AutoDownscalePre_x4", "Upscale_CNN_x2_M")
        assertEquals(hq.map { "Anime4K_$it.glsl" }, Anime4K.shaders(Anime4K.Mode.A, fast = false).map { it.name })
        val fast = listOf("Clamp_Highlights", "Restore_CNN_M", "Upscale_CNN_x2_M", "AutoDownscalePre_x2", "AutoDownscalePre_x4", "Upscale_CNN_x2_S")
        assertEquals(fast.map { "Anime4K_$it.glsl" }, Anime4K.shaders(Anime4K.Mode.A, fast = true).map { it.name })
    }
}
