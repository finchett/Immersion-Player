package io.github.immersionplayer.desktop

import io.github.immersionplayer.desktop.mpv.MpvPlayer
import java.io.File

/**
 * Anime4K's GLSL shaders (https://github.com/bloc97/Anime4K), bundled under resources/anime4k,
 * in the modes its mpv instructions set up on Ctrl+1–6. Each mode suits a source resolution:
 * A for 1080p, B for 720p, C for 480p; the doubled ones restore twice, sharper but heavier.
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

    enum class Quality(val label: String) {
        /** HQ if a benchmark says this GPU keeps up with it, else Fast. */
        AUTO("Auto"),
        /** The smaller networks: what most GPUs keep up with. */
        FAST("Fast"),
        /** The largest networks, as in Anime4K's own HQ setup. */
        HQ("HQ"),
    }

    /** What [benchmark] found: HQ Mode A's frame rate on [gpu], drawing [width] × [height] pixels. */
    data class Benchmark(val gpu: String, val width: Int, val height: Int, val hqFps: Double) {
        /** Whether HQ keeps up: video is 24 fps, and playback needs room besides the shaders. */
        val hq: Boolean get() = hqFps >= HQ_MIN_FPS

        fun encode() = "$gpu|$width|$height|$hqFps"

        companion object {
            fun decode(value: String): Benchmark? {
                val parts = value.split('|')
                if (parts.size != 4) return null
                return Benchmark(
                    parts[0],
                    parts[1].toIntOrNull() ?: return null,
                    parts[2].toIntOrNull() ?: return null,
                    parts[3].toDoubleOrNull() ?: return null,
                )
            }
        }
    }

    const val HQ_MIN_FPS = 30.0

    /** Whether [quality] means the lighter networks. Auto is Fast until a benchmark says HQ keeps up. */
    fun fast(quality: Quality, benchmark: Benchmark?): Boolean = when (quality) {
        Quality.FAST -> true
        Quality.HQ -> false
        Quality.AUTO -> benchmark?.hq != true
    }

    /**
     * Plays a test pattern through HQ Mode A as fast as this GPU can draw it at [width] × [height]
     * (the screen, so full screen keeps up too) and counts the frames. About five seconds; null
     * where mpv can't run shaders. Call off the UI thread.
     */
    fun benchmark(width: Int, height: Int): Benchmark? {
        val player = MpvPlayer(mapOf("untimed" to "yes", "audio" to "no"))
        try {
            val gpu = player.gpu ?: return null
            player.setShaders(shaders(Mode.A, fast = false))
            player.setTargetSize(width, height)
            player.load("av://lavfi:testsrc2=size=1920x1080:rate=24")
            player.setPaused(false)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (player.frame.value == null) {
                if (System.nanoTime() > deadline) return null
                Thread.sleep(50)
            }
            // the first frames wait on the shaders compiling
            Thread.sleep(1000)
            val startSerial = player.frame.value!!.serial
            val start = System.nanoTime()
            Thread.sleep(3000)
            val frames = player.frame.value!!.serial - startSerial
            return Benchmark(gpu, width, height, frames / ((System.nanoTime() - start) / 1e9))
        } finally {
            player.destroy()
        }
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
