package app.mizune.desktop

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

    /** Driver names as each OS reports them, against what Auto should pick. */
    @Test fun bestOnlyOnPowerfulGpus() {
        fun best(gpu: String, cores: Int? = null) = Anime4K.Hardware(gpu, cores).best
        // Apple: ordinary chips, Pros and the smaller Max GPUs get Fast
        assertFalse(best("Apple M1", 8))
        assertFalse(best("Apple M3 Pro", 18))
        assertFalse(best("Apple M4 Pro", 20))
        assertFalse(best("Apple M1 Max", 32))
        assertFalse(best("Apple M2 Max", 30))
        // the larger Max GPUs and the Ultras get Best
        assertTrue(best("Apple M2 Max", 38))
        assertTrue(best("Apple M3 Max", 40))
        assertTrue(best("Apple M4 Max", 32))
        assertTrue(best("Apple M1 Ultra", 48))
        // core count unknown: the tier's usual one; a later chip is judged at M4's rate
        assertFalse(best("Apple M2 Pro"))
        assertTrue(best("Apple M3 Ultra"))
        assertTrue(best("Apple M7 Max", 40))

        // NVIDIA, as Windows and Linux drivers name it; Anime4K's split is GTX 1060 / GTX 1080
        assertFalse(best("NVIDIA GeForce GTX 980/PCIe/SSE2"))
        assertFalse(best("NVIDIA GeForce GTX 1060 6GB/PCIe/SSE2"))
        assertFalse(best("NVIDIA GeForce GTX 1660 SUPER/PCIe/SSE2"))
        assertTrue(best("NVIDIA GeForce GTX 1080 Ti/PCIe/SSE2"))
        assertFalse(best("NVIDIA GeForce RTX 3050 Laptop GPU/PCIe/SSE2"))
        assertTrue(best("NVIDIA GeForce RTX 2070 SUPER/PCIe/SSE2"))
        assertTrue(best("NVIDIA GeForce RTX 3060/PCIe/SSE2"))
        assertTrue(best("NVIDIA GeForce RTX 4090/PCIe/SSE2"))
        assertTrue(best("NVIDIA TITAN RTX/PCIe/SSE2"))
        assertTrue(best("Quadro RTX 4000/PCIe/SSE2"))
        assertFalse(best("NVIDIA RTX 2000 Ada Generation/PCIe/SSE2"))
        assertTrue(best("NVIDIA RTX A5000/PCIe/SSE2"))

        // AMD: RX 570 / RX 590, 5700 XT and 6600 XT up, Vega 56
        assertFalse(best("AMD Radeon RX 570 Series"))
        assertTrue(best("Radeon RX 590 Series"))
        assertFalse(best("AMD Radeon RX 6500 XT (radeonsi, navi24, LLVM 15.0.7, DRM 3.49, 6.1.0)"))
        assertTrue(best("AMD Radeon RX 5700 XT"))
        assertTrue(best("AMD Radeon RX 6600 XT (radeonsi, navi23, LLVM 15.0.7, DRM 3.49, 6.1.0)"))
        assertTrue(best("AMD Radeon RX 7800 XT"))
        assertTrue(best("AMD Radeon RX 9070 XT"))
        assertTrue(best("Radeon RX Vega 56"))
        assertTrue(best("AMD Radeon Pro Vega 56 OpenGL Engine"))
        assertFalse(best("AMD Radeon Pro 5500M OpenGL Engine"))
        assertFalse(best("AMD Radeon 780M (radeonsi, gfx1103_r1, LLVM 17.0.6, DRM 3.57, 6.8.0)"))

        // Intel: integrated graphics get Fast, the bigger Arc cards Best
        assertFalse(best("Intel(R) Iris(TM) Plus Graphics OpenGL Engine"))
        assertFalse(best("Mesa Intel(R) UHD Graphics 620 (KBL GT2)"))
        assertFalse(best("Intel(R) Arc(TM) Graphics"))
        assertFalse(best("Intel(R) Arc(TM) A380 Graphics"))
        assertTrue(best("Intel(R) Arc(TM) A770 Graphics"))
        assertTrue(best("Intel(R) Arc(TM) B580 Graphics"))

        // anything else: Fast
        assertFalse(best("llvmpipe (LLVM 15.0.7, 256 bits)"))
        assertFalse(best("unknown"))
    }

    @Test fun modeFollowsTheVideosResolution() {
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(0))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(1080))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(2160))
        assertEquals(Anime4K.Mode.B, Anime4K.modeFor(720))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(480))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(576))
    }

    @Test fun readsAppleGpuCoresFromTheIoRegistry() {
        val out = """
            +-o AGXAcceleratorG13X  <class AGXAcceleratorG13X, id 0x1000003d0>
                {
                  "model" = "Apple M1 Pro"
                  "gpu-core-count" = 14
                }
        """
        assertEquals(14, Anime4K.Hardware.coresFromRegistry(out))
        assertEquals(null, Anime4K.Hardware.coresFromRegistry("nothing here"))
    }

    /** The real lookup: asks this machine's GPU driver, where shaders run (macOS for now). */
    @Test fun detectsThisMachine() {
        assumeTrue(Platform.isMac())
        val hardware = Anime4K.Hardware.detect()
        println("hardware: $hardware")
        assertNotNull(hardware)
        assertTrue(hardware!!.gpu, hardware.gpu.isNotBlank() && hardware.gpu != "unknown")
    }
}
