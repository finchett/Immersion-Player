package io.github.immersionplayer.anki

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * An animated WebP, built from frames Android encodes as ordinary WebP stills. Android can
 * encode WebP but not animate it; an animation is only those stills' bitstreams, each in an
 * ANMF chunk with its size and duration, behind a VP8X header that says it's animated.
 * (Every frame is a keyframe, so it's bigger than a real video codec would make it.)
 */
class AnimatedWebp(private val quality: Int) {
    private class Frame(val x: Int, val y: Int, val width: Int, val height: Int, val chunks: ByteArray, var durationMs: Int)

    private val frames = mutableListOf<Frame>()

    val isEmpty get() = frames.isEmpty()

    /** Adds [bitmap], drawn at [x], [y] (even numbers) over what the frames before left. */
    fun add(bitmap: Bitmap, durationMs: Int, x: Int = 0, y: Int = 0) {
        val still = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, still)
        frames += Frame(x, y, bitmap.width, bitmap.height, imageChunks(still.toByteArray()), durationMs)
    }

    /** Keeps the last frame on screen [ms] longer, instead of adding one that looks the same. */
    fun extendLast(ms: Int) {
        frames.lastOrNull()?.let { it.durationMs += ms }
    }

    fun build(): ByteArray {
        val width = frames.maxOf { it.x + it.width }
        val height = frames.maxOf { it.y + it.height }
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        chunk(body, "VP8X", ByteArrayOutputStream().apply {
            val alpha = frames.any { String(it.chunks, 0, 4) == "ALPH" }
            write(0x02 or if (alpha) 0x10 else 0) // animation, and alpha if a frame has it
            write(ByteArray(3))
            int24(this, width - 1)
            int24(this, height - 1)
        }.toByteArray())
        chunk(body, "ANIM", ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // background colour
            write(byteArrayOf(0, 0)) // loop forever
        }.toByteArray())
        for (frame in frames) {
            chunk(body, "ANMF", ByteArrayOutputStream().apply {
                int24(this, frame.x / 2)
                int24(this, frame.y / 2)
                int24(this, frame.width - 1)
                int24(this, frame.height - 1)
                int24(this, frame.durationMs.coerceIn(1, 0xFFFFFF))
                write(0x02) // don't blend with the frame before; nothing to dispose
                write(frame.chunks)
            }.toByteArray())
        }
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        int32(out, body.size())
        body.writeTo(out)
        return out.toByteArray()
    }

    companion object {
        /** The image chunks of a still WebP (VP8 or VP8L, and ALPH if it has one), without its RIFF header or VP8X. */
        fun imageChunks(webp: ByteArray): ByteArray {
            require(String(webp, 0, 4) == "RIFF" && String(webp, 8, 4) == "WEBP") { "not a WebP" }
            val out = ByteArrayOutputStream()
            var at = 12
            while (at + 8 <= webp.size) {
                val name = String(webp, at, 4)
                val size = (webp[at + 4].toInt() and 0xFF) or ((webp[at + 5].toInt() and 0xFF) shl 8) or
                    ((webp[at + 6].toInt() and 0xFF) shl 16) or ((webp[at + 7].toInt() and 0xFF) shl 24)
                val total = 8 + size + (size and 1)
                if (name == "VP8 " || name == "VP8L" || name == "ALPH") out.write(webp, at, minOf(total, webp.size - at))
                at += total
            }
            return out.toByteArray()
        }

        private fun chunk(out: ByteArrayOutputStream, name: String, data: ByteArray) {
            out.write(name.toByteArray())
            int32(out, data.size)
            out.write(data)
            if (data.size % 2 == 1) out.write(0)
        }

        private fun int24(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write(value shr 8 and 0xFF)
            out.write(value shr 16 and 0xFF)
        }

        private fun int32(out: ByteArrayOutputStream, value: Int) {
            int24(out, value)
            out.write(value ushr 24 and 0xFF)
        }
    }
}
