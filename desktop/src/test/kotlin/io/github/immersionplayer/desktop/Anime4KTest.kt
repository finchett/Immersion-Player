package io.github.immersionplayer.desktop

import com.sun.jna.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    @Test fun autoGoesByTheGpu() {
        val m1Pro = Anime4K.Hardware("Apple M1 Pro", 14)
        val m4Max = Anime4K.Hardware("Apple M4 Max", 40)
        assertTrue(Anime4K.fast(Anime4K.Preset.AUTO, m1Pro))
        assertFalse(Anime4K.fast(Anime4K.Preset.AUTO, m4Max))
        assertTrue("unknown hardware gets Fast", Anime4K.fast(Anime4K.Preset.AUTO, null))
        // a fixed choice ignores the hardware
        assertTrue(Anime4K.fast(Anime4K.Preset.FAST, m4Max))
        assertFalse(Anime4K.fast(Anime4K.Preset.BEST, m1Pro))
    }

    @Test fun bestOnlyOnTheBigGpus() {
        fun best(chip: String, cores: Int?) = Anime4K.Hardware(chip, cores).best
        // ordinary chips, Pros, and the smaller Max GPUs: Fast
        assertFalse(best("Apple M1", 8))
        assertFalse(best("Apple M3 Pro", 18))
        assertFalse(best("Apple M4 Pro", 20))
        assertFalse(best("Apple M1 Max", 32))
        assertFalse(best("Apple M2 Max", 30))
        // the larger Max GPUs and the Ultras: Best
        assertTrue(best("Apple M2 Max", 38))
        assertTrue(best("Apple M3 Max", 40))
        assertTrue(best("Apple M4 Max", 32))
        assertTrue(best("Apple M1 Ultra", 48))
        // core count unknown: the tier's usual one
        assertFalse(best("Apple M2 Pro", null))
        assertTrue(best("Apple M3 Ultra", null))
        // a later chip than this knows of is judged at M4's rate
        assertTrue(best("Apple M7 Max", 40))
        // not Apple Silicon (Intel Macs): Fast
        assertFalse(best("Intel(R) Iris(TM) Plus Graphics", null))
        assertFalse(best("AMD Radeon Pro 5500M", null))
    }

    @Test fun modeFollowsTheVideosResolution() {
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(0))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(1080))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(2160))
        assertEquals(Anime4K.Mode.B, Anime4K.modeFor(720))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(480))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(576))
    }

    @Test fun readsTheIoRegistry() {
        val out = """
            +-o AGXAcceleratorG13X  <class AGXAcceleratorG13X, id 0x1000003d0>
                {
                  "model" = "Apple M1 Pro"
                  "gpu-core-count" = 14
                }
        """
        assertEquals(Anime4K.Hardware("Apple M1 Pro", 14), Anime4K.Hardware.fromRegistry(out))
        assertEquals(null, Anime4K.Hardware.fromRegistry("nothing here"))
    }

    /** The real lookup, on whatever Mac runs the tests. */
    @Test fun detectsThisMachine() {
        assumeTrue(Platform.isMac())
        val hardware = Anime4K.Hardware.detect()
        println("hardware: $hardware")
        assertNotNull(hardware)
        assertTrue(hardware!!.chip, hardware.chip.isNotBlank())
    }
}
