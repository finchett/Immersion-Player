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

    @Test fun autoIsFastUntilABenchmarkSaysBestKeepsUp() {
        val slow = Anime4K.Benchmark("Apple M1 Pro", 3024, 1964, 6.5)
        val quick = slow.copy(bestFps = 45.0)
        assertTrue(Anime4K.fast(Anime4K.Preset.AUTO, null))
        assertTrue(Anime4K.fast(Anime4K.Preset.AUTO, slow))
        assertFalse(Anime4K.fast(Anime4K.Preset.AUTO, quick))
        // a fixed choice ignores the benchmark
        assertTrue(Anime4K.fast(Anime4K.Preset.FAST, quick))
        assertFalse(Anime4K.fast(Anime4K.Preset.BEST, slow))
    }

    @Test fun modeFollowsTheVideosResolution() {
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(0))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(1080))
        assertEquals(Anime4K.Mode.A, Anime4K.modeFor(2160))
        assertEquals(Anime4K.Mode.B, Anime4K.modeFor(720))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(480))
        assertEquals(Anime4K.Mode.C, Anime4K.modeFor(576))
    }

    @Test fun benchmarkSurvivesBeingStored() {
        val benchmark = Anime4K.Benchmark("Apple M1 Pro", 3024, 1964, 6.53)
        assertEquals(benchmark, Anime4K.Benchmark.decode(benchmark.encode()))
        assertEquals(null, Anime4K.Benchmark.decode("garbage"))
    }

    /** Runs the real benchmark: libmpv drawing HQ Mode A through OpenGL. macOS only. */
    @Test fun benchmarkMeasuresThisGpu() {
        assumeTrue(Platform.isMac())
        val benchmark = Anime4K.benchmark(1920, 1080)
        assertNotNull(benchmark)
        println("benchmark: $benchmark")
        assertTrue(benchmark!!.gpu, benchmark.gpu.isNotBlank() && benchmark.gpu != "unknown")
        assertTrue("${benchmark.bestFps} fps", benchmark.bestFps > 1)
    }
}
