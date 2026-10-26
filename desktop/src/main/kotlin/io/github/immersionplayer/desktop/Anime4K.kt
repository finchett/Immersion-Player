package io.github.immersionplayer.desktop

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
        /** Best if this Mac's GPU should keep up with it, else Fast: see [Hardware]. */
        AUTO("Auto"),
        /** The smaller networks: what most GPUs keep up with. */
        FAST("Fast"),
        /** The largest networks, Anime4K's own HQ setup. */
        BEST("Best"),
    }

    /**
     * What Auto goes by: the Apple Silicon chip ("Apple M1 Pro") and its GPU's core count, when
     * macOS says. From these comes a rough estimate of the GPU's compute, enough to tell whether
     * Best keeps up without anyone having to try it.
     */
    data class Hardware(val chip: String, val gpuCores: Int?) {
        /** Rough FP32 TFLOPS: cores × each generation's rate per core. Null for non-Apple GPUs. */
        val teraflops: Double? get() {
            val match = APPLE_CHIP.matchEntire(chip.trim()) ?: return null
            val generation = match.groupValues[1].toInt()
            val cores = gpuCores ?: TYPICAL_CORES.getValue(match.groupValues[2].trim())
            // M1 2.6 TFLOPS from 8 cores, M2 3.6 from 10, M3 and M4 about 4.1–4.3 from 10;
            // later chips get M4's rate, which undersells them, if anything
            val perCore = when (generation) {
                1 -> 0.33
                2 -> 0.36
                3 -> 0.41
                else -> 0.43
            }
            return cores * perCore
        }

        /** Whether Best should keep up here; Fast if the GPU is unknown or not Apple's. */
        val best: Boolean get() = (teraflops ?: 0.0) >= BEST_MIN_TERAFLOPS

        companion object {
            private val APPLE_CHIP = Regex("""Apple M(\d+)( Pro| Max| Ultra)?""")
            private val TYPICAL_CORES = mapOf("" to 8, "Pro" to 16, "Max" to 32, "Ultra" to 64)

            /** Reads them from the GPU's entry in the I/O Registry; null off macOS, or if it fails. */
            fun detect(): Hardware? = runCatching {
                if (!System.getProperty("os.name").lowercase().contains("mac")) return null
                val process = ProcessBuilder("ioreg", "-rc", "AGXAccelerator", "-d", "1").redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                fromRegistry(out)
            }.getOrNull()

            /** Parses `ioreg -rc AGXAccelerator` output: its "model" and "gpu-core-count". */
            fun fromRegistry(out: String): Hardware? {
                val chip = Regex(""""model" = "([^"]+)"""").find(out)?.groupValues?.get(1) ?: return null
                val cores = Regex(""""gpu-core-count" = (\d+)""").find(out)?.groupValues?.get(1)?.toIntOrNull()
                return Hardware(chip, cores)
            }
        }
    }

    /**
     * Best needs about this much. On an M1 Pro (14 cores, about 4.6 TFLOPS) Best Mode A drew 1080p
     * at 11 fps; 24 fps video, with room for decoding and drawing the rest of the window, wants
     * about 30, so about 2.6 times that GPU. That puts Max chips with the larger GPUs (M2 Max
     * 38-core and up) and the Ultras on Best, and every other Mac on Fast.
     */
    const val BEST_MIN_TERAFLOPS = 12.0

    /** This Mac's, read once; null off macOS or where macOS doesn't say. */
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
