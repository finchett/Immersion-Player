package io.github.immersionplayer.anki

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/**
 * A stretch of a video's audio as mono Opus in Ogg, made with Android's own codecs: the video
 * file is decoded to PCM by MediaCodec, and Android 10's Opus encoder and Ogg muxer write the
 * clip. (The app's libmpv can't encode audio, and is busy playing anyway.)
 */
object AudioClipper {
    private const val RATE = 48_000
    private const val TIMEOUT_US = 10_000L

    /** [start]..[end] seconds of audio track [audioIndex] (0 = the first), or the first track. */
    fun clip(context: Context, video: Uri, start: Double, end: Double, audioIndex: Int?, out: File): Boolean {
        val pcm = decode(context, video, start.coerceAtLeast(0.0), end, audioIndex) ?: return false
        if (pcm.isEmpty()) return false
        encode(pcm, out)
        return out.length() > 0
    }

    /** The audio between [start] and [end] as mono samples at [RATE]. */
    private fun decode(context: Context, video: Uri, start: Double, end: Double, audioIndex: Int?): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, video, null)
            val audioTracks = (0 until extractor.trackCount).filter {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
            val track = audioTracks.getOrNull(audioIndex ?: 0) ?: audioTracks.firstOrNull() ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val startUs = (start * 1_000_000).toLong()
            val endUs = (end * 1_000_000).toLong()
            // a second early, to be sure the seek lands before the start; what's before it is dropped below
            extractor.seekTo((startUs - 1_000_000).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val samples = FloatList()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var encoding = AudioFormat.ENCODING_PCM_16BIT
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                // the decoded audio is continuous, so time is counted in samples from the first
                // buffer: decoders don't stamp every buffer (Android's FLAC decoder splits its
                // output into 16 KB pieces that share one time)
                var firstUs = -1L
                var decodedFrames = 0L
                while (true) {
                    if (!inputDone) {
                        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                            // a little past the end, for decoders that hold frames back
                            if (size < 0 || extractor.sampleTime > endUs + 500_000) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val output = decoder.outputFormat
                            rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        index >= 0 -> {
                            val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frames = info.size / (bytes * channels)
                            if (firstUs < 0 && info.size > 0) firstUs = info.presentationTimeUs
                            for (frame in 0 until frames) {
                                val timeUs = firstUs + decodedFrames++ * 1_000_000L / rate
                                var sum = 0f
                                for (c in 0 until channels) {
                                    sum += if (bytes == 4) buffer.float else buffer.short / 32768f
                                }
                                if (timeUs in startUs until endUs) samples.add(sum / channels)
                            }
                            decoder.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            } finally {
                runCatching { decoder.stop() }
                decoder.release()
            }
            return resample(samples.toArray(), rate)
        } finally {
            extractor.release()
        }
    }

    /** Linear resampling to [RATE]; plenty for speech in a card. */
    private fun resample(input: FloatArray, from: Int): FloatArray {
        if (from == RATE || input.isEmpty()) return input
        val out = FloatArray((input.size.toLong() * RATE / from).toInt())
        val step = from.toDouble() / RATE
        for (i in out.indices) {
            val position = i * step
            val j = position.toInt().coerceAtMost(input.lastIndex)
            val next = (j + 1).coerceAtMost(input.lastIndex)
            val t = (position - j).toFloat()
            out[i] = input[j] * (1 - t) + input[next] * t
        }
        return out
    }

    private fun encode(pcm: FloatArray, out: File) {
        out.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        var track = -1
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var fed = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = encoder.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val timeUs = fed * 1_000_000L / RATE
                        if (fed >= pcm.size) {
                            // end of stream in a buffer of its own: some encoders drop the samples
                            // of a buffer that also carries the flag
                            encoder.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val count = minOf(buffer.remaining() / 2, pcm.size - fed)
                            for (i in 0 until count) {
                                buffer.putShort((pcm[fed + i].coerceIn(-1f, 1f) * 32767).toInt().toShort())
                            }
                            fed += count
                            encoder.queueInputBuffer(index, 0, count * 2, timeUs, 0)
                        }
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)!!
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && track >= 0) muxer.writeSampleData(track, buffer, info)
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
        }
    }

    /** A growable float array, so a minute of audio isn't boxed. */
    private class FloatList {
        private var data = FloatArray(RATE * 4)
        private var size = 0
        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }
        fun toArray() = data.copyOf(size)
    }
}
