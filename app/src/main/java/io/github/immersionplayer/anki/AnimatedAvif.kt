package io.github.immersionplayer.anki

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * An animated AVIF made with the phone's AV1 encoder, the same kind of picture desktop cards
 * get. Android can't write AVIF, but an AVIF image sequence is an MP4 of AV1 with its brand and
 * track type changed ("avis", "pict"), so MediaMuxer writes the MP4 and [asAvif] patches those
 * few bytes. Frames go in one at a time; [finish] writes the file.
 */
class AnimatedAvif(private val out: File, val width: Int, val height: Int, private val fps: Int) {
    private val encoder = MediaCodec.createEncoderByType(MIME)
    private val mp4 = File(out.path + ".mp4")
    private val muxer = MediaMuxer(mp4.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var frames = 0
    private val info = MediaCodec.BufferInfo()

    init {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_BIT_RATE, BITS_PER_PIXEL_SECOND * width * height / 1000)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            // one key frame: a line is only a few seconds
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 60)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    /** Adds [bitmap] (the size given at the start) as the next frame. */
    fun add(bitmap: Bitmap) {
        val deadline = System.currentTimeMillis() + STALL_MS
        while (true) {
            val index = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                check(System.currentTimeMillis() < deadline) { "the encoder stopped taking frames" }
                drain(false)
                continue
            }
            val image = encoder.getInputImage(index) ?: error("no input image")
            writeYuv(bitmap, image)
            encoder.queueInputBuffer(index, 0, width * height * 3 / 2, frames * 1_000_000L / fps, 0)
            frames++
            drain(false)
            return
        }
    }

    /** Ends the animation and writes it to the output file; false if there was nothing to write. */
    fun finish(): Boolean {
        try {
            if (frames == 0) return false
            val deadline = System.currentTimeMillis() + STALL_MS
            while (true) {
                check(System.currentTimeMillis() < deadline) { "the encoder didn't finish" }
                val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    encoder.queueInputBuffer(index, 0, 0, frames * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drain(false)
            }
            drain(true)
            muxer.stop()
            out.writeBytes(asAvif(mp4.readBytes()))
            return true
        } finally {
            release()
        }
    }

    fun release() {
        runCatching { encoder.stop() }
        encoder.release()
        runCatching { muxer.release() }
        mp4.delete()
    }

    private fun drain(untilEnd: Boolean) {
        val deadline = System.currentTimeMillis() + STALL_MS
        while (true) {
            check(System.currentTimeMillis() < deadline) { "the encoder stopped giving frames back" }
            val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && track >= 0) muxer.writeSampleData(track, encoder.getOutputBuffer(index)!!, info)
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                // nothing ready: carry on feeding, unless everything is in and this is the wait for the end
                !untilEnd -> return
            }
        }
    }

    /** RGB to the encoder's YUV 4:2:0 (BT.601, limited range), whatever its plane layout. */
    private fun writeYuv(bitmap: Bitmap, image: android.media.Image) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val (yPlane, uPlane, vPlane) = image.planes
        val y = yPlane.buffer
        for (row in 0 until height) {
            for (col in 0 until width) {
                val p = pixels[row * width + col]
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF
                y.put(row * yPlane.rowStride + col * yPlane.pixelStride, ((66 * r + 129 * g + 25 * b + 128 shr 8) + 16).toByte())
            }
        }
        val u = uPlane.buffer
        val v = vPlane.buffer
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                // the average of the 2×2 block
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val p = pixels[(row * 2 + dy) * width + col * 2 + dx]
                    r += p shr 16 and 0xFF
                    g += p shr 8 and 0xFF
                    b += p and 0xFF
                }
                r /= 4; g /= 4; b /= 4
                u.put(row * uPlane.rowStride + col * uPlane.pixelStride, ((-38 * r - 74 * g + 112 * b + 128 shr 8) + 128).toByte())
                v.put(row * vPlane.rowStride + col * vPlane.pixelStride, ((112 * r - 94 * g - 18 * b + 128 shr 8) + 128).toByte())
            }
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AV1
        private const val TIMEOUT_US = 10_000L

        /** How long the encoder may sit without progress before the WebP is made instead. */
        private const val STALL_MS = 10_000L

        /** About 150 kbit/s at 640×360: roughly 25 KB a second, close to desktop's AVIF of the same line. */
        private const val BITS_PER_PIXEL_SECOND = 650

        /** Whether this phone can encode AV1 at all. */
        fun supported(): Boolean = runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .any { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) } }
        }.getOrDefault(false)

        /**
         * An MP4 of AV1 as an AVIF image sequence: the file type box's brands become "avis" (and
         * the brands AVIF readers look for), and the video track's handler "vide" becomes "pict".
         * Both keep their sizes, so nothing else in the file moves.
         */
        fun asAvif(mp4: ByteArray): ByteArray {
            val data = mp4.copyOf()
            require(String(data, 4, 4) == "ftyp") { "not an MP4" }
            val ftypSize = int32(data, 0)
            "avis".toByteArray().copyInto(data, 8)
            val brands = listOf("avis", "msf1", "iso8", "mif1", "miaf", "MA1B")
            val slots = (ftypSize - 16) / 4
            for (i in 0 until slots) brands[minOf(i, brands.lastIndex)].toByteArray().copyInto(data, 16 + 4 * i)
            var at = 0
            while (true) {
                at = indexOf(data, "hdlr", at + 1)
                if (at < 0) break
                // hdlr: size, "hdlr", version and flags, pre_defined, then the handler type
                if (String(data, at + 12, 4) == "vide") "pict".toByteArray().copyInto(data, at + 12)
            }
            return data
        }

        private fun int32(data: ByteArray, at: Int) =
            (data[at].toInt() and 0xFF shl 24) or (data[at + 1].toInt() and 0xFF shl 16) or
                (data[at + 2].toInt() and 0xFF shl 8) or (data[at + 3].toInt() and 0xFF)

        private fun indexOf(data: ByteArray, text: String, from: Int): Int {
            val bytes = text.toByteArray()
            outer@ for (i in from..data.size - bytes.size) {
                for (j in bytes.indices) if (data[i + j] != bytes[j]) continue@outer
                return i
            }
            return -1
        }
    }
}
