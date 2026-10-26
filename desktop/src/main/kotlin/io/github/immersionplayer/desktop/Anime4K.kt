package io.github.immersionplayer.desktop

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
