package io.github.immersionplayer.desktop

import io.github.immersionplayer.desktop.mpv.OffscreenGl
import java.io.File

/**
 * Anime4K's GLSL shaders (https://github.com/bloc97/Anime4K), bundled under resources/anime4k,
 * in the modes its mpv instructions set up on Ctrl+1–6. Each mode suits a source resolution:
 * A for 1080p, B for 720p, C for 480p; the doubled ones restore twice, sharper but heavier.
 *
 * What people choose is a [Preset]: how heavy a network to run, or off. The mode follows the
 * video's resolution unless one is picked under Advanced.
 */
object Anime4K {
    enum class Mode(val label: String, val detail: String) {
        A("Mode A", "For 1080p video, or blurry 720p"),
        B("Mode B", "For 720p video, or sharper 1080p"),
        C("Mode C", "For 480p video, or noisy footage"),
        AA("Mode A+A", "Mode A, restored twice"),
        BB("Mode B+B", "Mode B, restored twice"),
        CA("Mode C+A", "Mode C, then restored as A"),
    }

    enum class Preset(val label: String) {
        OFF("Off"),
        /** Best if this GPU should keep up with it, else Fast: see [Hardware]. */
        AUTO("Auto"),
        /** The smaller networks: what most GPUs keep up with. */
        FAST("Fast"),
        /** The largest networks, Anime4K's own HQ setup. */
        BEST("Best"),
    }

    /**
     * What Auto goes by: the GPU's name as its OpenGL driver gives it ("Apple M1 Pro", "NVIDIA
     * GeForce RTX 3060/PCIe/SSE2", "AMD Radeon RX 6700 XT (radeonsi, …)"), plus, for Apple's
     * GPUs, the core count macOS reports. From these comes an educated guess at whether Best
     * keeps up, so nobody has to work out what their machine can run.
     */
    data class Hardware(val gpu: String, val gpuCores: Int? = null) {
        /** Whether Best should keep up here. Fast for anything it doesn't recognise. */
        val best: Boolean get() {
            val name = gpu.uppercase()
            APPLE.find(name)?.let { return appleTeraflops(it, gpuCores) >= BEST_MIN_TERAFLOPS }
            // Anime4K's own guidance: Fast for the likes of a GTX 980, GTX 1060 or RX 570; HQ (Best)
            // from a GTX 1080, RTX 2070 or 3060, RX 590, Vega 56, 5700 XT or 6600 XT up
            number(RTX, name)?.let { n ->
                // round thousands are workstation cards (Quadro RTX 4000, RTX 4000 Ada); the rest
                // are GeForce RTX 2050 to 5090, where the last two digits are the tier
                return if (n % 1000 == 0) n >= 4000 else n % 100 >= 60
            }
            number(WORKSTATION_RTX, name)?.let { return it >= 4000 }
            number(GTX, name)?.let { return it in 1080..1099 }
            if ("TITAN" in name || "RADEON VII" in name || VEGA_DISCRETE.containsMatchIn(name)) return true
            number(RADEON_RX, name)?.let { n ->
                return when {
                    n < 1000 -> n >= 590 // RX 4xx/5xx Polaris
                    n >= 9000 -> n % 100 >= 60 // RX 9060, 9070
                    else -> n % 1000 >= 600 // RX 5600+, 6600+, 7600+
                }
            }
            number(RADEON_PRO_W, name)?.let { return it % 1000 >= 600 }
            number(INTEL_ARC, name)?.let { return it >= 570 } // A580/A750/A770, B570/B580
            return false
        }

        companion object {
            private val APPLE = Regex("""APPLE M(\d+)( PRO| MAX| ULTRA)?""")
            private val RTX = Regex("""RTX\s*(\d{4})""")
            private val WORKSTATION_RTX = Regex("""RTX\s*A(\d{4})""")
            private val GTX = Regex("""GTX\s*(\d{3,4})""")
            private val RADEON_RX = Regex("""\bRX\s*(\d{3,4})""")
            private val RADEON_PRO_W = Regex("""RADEON PRO W(\d{4})""")
            private val VEGA_DISCRETE = Regex("""VEGA\s*(56|64)|VEGA II""")
            private val INTEL_ARC = Regex("""ARC.*?\b[AB](\d{3})""")
            private val TYPICAL_CORES = mapOf("" to 8, "PRO" to 16, "MAX" to 32, "ULTRA" to 64)

            private fun number(pattern: Regex, name: String) = pattern.find(name)?.groupValues?.get(1)?.toIntOrNull()

            /** Rough FP32 TFLOPS: cores × each generation's rate per core. */
            private fun appleTeraflops(match: MatchResult, cores: Int?): Double {
                val generation = match.groupValues[1].toInt()
                val count = cores ?: TYPICAL_CORES.getValue(match.groupValues[2].trim())
                // M1 2.6 TFLOPS from 8 cores, M2 3.6 from 10, M3 and M4 about 4.1–4.3 from 10;
                // later chips get M4's rate, which undersells them, if anything
                val perCore = when (generation) {
                    1 -> 0.33
                    2 -> 0.36
                    3 -> 0.41
                    else -> 0.43
                }
                return count * perCore
            }

            /**
             * Asks the GPU's driver for its name, through the same OpenGL the shaders run on, so
             * this works wherever they do; null where they can't run. On macOS, also reads Apple
             * GPUs' core count from the I/O Registry.
             */
            fun detect(): Hardware? {
                val gpu = runCatching { OffscreenGl.gpuName() }.getOrNull() ?: return null
                return Hardware(gpu, if (APPLE.containsMatchIn(gpu.uppercase())) appleGpuCores() else null)
            }

            private fun appleGpuCores(): Int? = runCatching {
                val process = ProcessBuilder("ioreg", "-rc", "AGXAccelerator", "-d", "1").redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                coresFromRegistry(out)
            }.getOrNull()

            /** The "gpu-core-count" in `ioreg -rc AGXAccelerator` output. */
            fun coresFromRegistry(out: String): Int? =
                Regex(""""gpu-core-count" = (\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
        }
    }

    /**
     * Best needs about this much from an Apple GPU. On an M1 Pro (14 cores, about 4.6 TFLOPS)
     * Best Mode A drew 1080p at 11 fps; 24 fps video, with room for decoding and drawing the rest
     * of the window, wants about 30, so about 2.6 times that GPU. That puts the larger Max GPUs
     * (M2 Max 38-core and up) and the Ultras on Best, in line with Anime4K's GTX 1080 / RTX 2070
     * bar for other GPUs.
     */
    const val BEST_MIN_TERAFLOPS = 12.0

    /** This machine's GPU, read once; null where shaders can't run. */
    val hardware: Hardware? by lazy { Hardware.detect() }

    /** Whether [preset] means the lighter networks. Auto is Best only on [hardware] it should keep up on. */
    fun fast(preset: Preset, hardware: Hardware?): Boolean = when (preset) {
        Preset.BEST -> false
        Preset.AUTO -> hardware?.best != true
        Preset.OFF, Preset.FAST -> true
    }

    /** The mode Anime4K suggests for a video [height] pixels tall; A while the height isn't known. */
    fun modeFor(height: Int): Mode = when {
        height <= 0 || height >= 900 -> Mode.A
        height >= 600 -> Mode.B
        else -> Mode.C
    }

    /** The shader files of [mode], in the order mpv runs them. [fast] uses the lighter networks. */
    fun shaders(mode: Mode, fast: Boolean): List<File> {
        // the HQ chain's largest networks; Fast steps each down a size (VL to M, M to S)
        val big = if (fast) "M" else "VL"
        val small = if (fast) "S" else "M"
        val names = when (mode) {
            Mode.A -> listOf("Restore_CNN_$big", "Upscale_CNN_x2_$big", *DOWNSCALE, "Upscale_CNN_x2_$small")
            Mode.B -> listOf("Restore_CNN_Soft_$big", "Upscale_CNN_x2_$big", *DOWNSCALE, "Upscale_CNN_x2_$small")
            Mode.C -> listOf("Upscale_Denoise_CNN_x2_$big", *DOWNSCALE, "Upscale_CNN_x2_$small")
            Mode.AA -> listOf("Restore_CNN_$big", "Upscale_CNN_x2_$big", "Restore_CNN_$small", *DOWNSCALE, "Upscale_CNN_x2_$small")
            Mode.BB -> listOf("Restore_CNN_Soft_$big", "Upscale_CNN_x2_$big", *DOWNSCALE, "Restore_CNN_Soft_$small", "Upscale_CNN_x2_$small")
            Mode.CA -> listOf("Upscale_Denoise_CNN_x2_$big", *DOWNSCALE, "Restore_CNN_$small", "Upscale_CNN_x2_$small")
        }
        return (listOf("Clamp_Highlights") + names).map(::file)
    }

    private val DOWNSCALE = arrayOf("AutoDownscalePre_x2", "AutoDownscalePre_x4")

    /** mpv reads shaders from files, so each is copied out of the jar into the cache once. */
    private fun file(name: String): File {
        val resource = "/anime4k/Anime4K_$name.glsl"
        val bytes = Anime4K::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: error("missing $resource")
        val file = File(AppDirs.cache, "anime4k/Anime4K_$name.glsl")
        if (!file.isFile || file.length() != bytes.size.toLong()) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        return file
    }
}
