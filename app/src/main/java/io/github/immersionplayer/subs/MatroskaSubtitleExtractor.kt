package io.github.immersionplayer.subs

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * Reads the text subtitle tracks (SRT, ASS/SSA, WebVTT) out of a Matroska/WebM file.
 *
 * Only element headers are read while walking clusters; audio and video frames are skipped
 * by seeking, so a full episode is scanned without reading most of the file.
 */
class MatroskaSubtitleExtractor(private val channel: FileChannel) {

    private class TrackInfo(
        var number: Long = 0,
        var type: Long = 0,
        var codecId: String = "",
        var language: String = "eng",
        var languageBcp47: String? = null,
        var name: String? = null,
        var compressionAlgo: Long? = null,
        var compressionSettings: ByteArray? = null,
    ) {
        val isTextSubtitle: Boolean
            get() = type == 17L && codecId in TEXT_CODECS

        val displayLanguage: String
            get() = languageBcp47 ?: language
    }

    private val reader = Reader(channel)
    private var timestampScale = 1_000_000L
    private val tracks = mutableMapOf<Long, TrackInfo>()
    private val cues = mutableMapOf<Long, MutableList<Cue>>()

    fun extract(): List<SubtitleTrack> {
        reader.position = 0
        while (reader.position < reader.size) {
            val id = reader.readId() ?: break
            val size = reader.readSize()
            when (id) {
                ID_SEGMENT -> {
                    val end = if (size < 0) reader.size else reader.position + size
                    parseSegment(end)
                }
                else -> if (size < 0) break else reader.skip(size)
            }
        }

        return tracks.values
            .filter { it.isTextSubtitle }
            .sortedBy { it.number }
            .map { track ->
                SubtitleTrack(
                    name = track.name ?: "Track ${track.number}",
                    language = track.displayLanguage,
                    cues = fillMissingEnds(cues[track.number].orEmpty()).normalized(),
                )
            }
    }

    private fun parseSegment(end: Long) {
        while (reader.position < end) {
            val elementStart = reader.position
            val id = reader.readId() ?: return
            val size = reader.readSize()
            when (id) {
                ID_INFO -> parseInfo(reader.position + size)
                ID_TRACKS -> {
                    parseTracks(reader.position + size)
                    if (tracks.values.none { it.isTextSubtitle }) return
                }
                ID_CLUSTER -> parseCluster(if (size < 0) -1 else reader.position + size)
                else -> {
                    if (size < 0) return
                    reader.skip(size)
                }
            }
            if (reader.position <= elementStart) return
        }
    }

    private fun parseInfo(end: Long) {
        while (reader.position < end) {
            val id = reader.readId() ?: return
            val size = reader.readSize()
            if (id == ID_TIMESTAMP_SCALE) timestampScale = reader.readUInt(size) else reader.skip(size)
        }
    }

    private fun parseTracks(end: Long) {
        while (reader.position < end) {
            val id = reader.readId() ?: return
            val size = reader.readSize()
            if (id == ID_TRACK_ENTRY) {
                val track = TrackInfo()
                parseTrackEntry(reader.position + size, track)
                tracks[track.number] = track
            } else {
                reader.skip(size)
            }
        }
    }

    private fun parseTrackEntry(end: Long, track: TrackInfo) {
        while (reader.position < end) {
            val id = reader.readId() ?: return
            val size = reader.readSize()
            when (id) {
                ID_TRACK_NUMBER -> track.number = reader.readUInt(size)
                ID_TRACK_TYPE -> track.type = reader.readUInt(size)
                ID_CODEC_ID -> track.codecId = reader.readString(size)
                ID_LANGUAGE -> track.language = reader.readString(size)
                ID_LANGUAGE_BCP47 -> track.languageBcp47 = reader.readString(size)
                ID_NAME -> track.name = reader.readString(size)
                ID_CONTENT_ENCODINGS -> parseContentEncodings(reader.position + size, track)
                else -> reader.skip(size)
            }
        }
    }

    private fun parseContentEncodings(end: Long, track: TrackInfo) {
        // Walks ContentEncoding > ContentCompression; other children are skipped.
        while (reader.position < end) {
            val id = reader.readId() ?: return
            val size = reader.readSize()
            when (id) {
                ID_CONTENT_ENCODING, ID_CONTENT_COMPRESSION -> {
                    if (track.compressionAlgo == null && id == ID_CONTENT_COMPRESSION) {
                        track.compressionAlgo = 0 // zlib is the default when absent
                    }
                    parseContentEncodings(reader.position + size, track)
                }
                ID_CONTENT_COMP_ALGO -> track.compressionAlgo = reader.readUInt(size)
                ID_CONTENT_COMP_SETTINGS -> track.compressionSettings = reader.readBytes(size)
                else -> reader.skip(size)
            }
        }
    }

    private fun parseCluster(end: Long) {
        var clusterTimestamp = 0L
        while (end < 0 || reader.position < end) {
            if (reader.position >= reader.size) return
            val elementStart = reader.position
            val id = reader.readId() ?: return
            if (end < 0 && id in TOP_LEVEL_IDS) {
                // unknown-size cluster ends at the next top-level element
                reader.position = elementStart
                return
            }
            val size = reader.readSize()
            if (size < 0) return
            when (id) {
                ID_CLUSTER_TIMESTAMP -> clusterTimestamp = reader.readUInt(size)
                ID_SIMPLE_BLOCK -> readBlock(reader.position, size, clusterTimestamp, null)
                ID_BLOCK_GROUP -> readBlockGroup(reader.position + size, clusterTimestamp)
                else -> reader.skip(size)
            }
        }
    }

    private fun readBlockGroup(end: Long, clusterTimestamp: Long) {
        var blockPos = -1L
        var blockSize = 0L
        var duration: Long? = null
        while (reader.position < end) {
            val id = reader.readId() ?: return
            val size = reader.readSize()
            when (id) {
                ID_BLOCK -> {
                    blockPos = reader.position
                    blockSize = size
                    reader.skip(size)
                }
                ID_BLOCK_DURATION -> duration = reader.readUInt(size)
                else -> reader.skip(size)
            }
        }
        if (blockPos >= 0) {
            val resume = reader.position
            readBlock(blockPos, blockSize, clusterTimestamp, duration)
            reader.position = resume
        }
    }

    private fun readBlock(start: Long, size: Long, clusterTimestamp: Long, duration: Long?) {
        val end = start + size
        reader.position = start
        val trackNumber = reader.readSize()
        val track = tracks[trackNumber]
        if (track == null || !track.isTextSubtitle) {
            reader.position = end
            return
        }
        val relativeTimestamp = reader.readInt16()
        val flags = reader.readByte()
        val lacing = (flags shr 1) and 0x3
        if (lacing != 0) {
            reader.position = end
            return
        }
        var data = reader.readBytes(end - reader.position)
        data = decode(track, data) ?: return

        val scale = timestampScale / 1_000_000_000.0
        val startSeconds = (clusterTimestamp + relativeTimestamp) * scale
        val endSeconds = if (duration != null) startSeconds + duration * scale else Double.NaN
        val raw = String(data, Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
        val text = when (track.codecId) {
            "S_TEXT/ASS", "S_TEXT/SSA" -> AssParser.parseBlockText(raw)
            else -> SrtParser.parseBlockText(raw)
        } ?: return
        cues.getOrPut(trackNumber) { mutableListOf() }.add(Cue(startSeconds, endSeconds, text))
    }

    private fun decode(track: TrackInfo, data: ByteArray): ByteArray? =
        when (track.compressionAlgo) {
            null -> data
            0L -> inflate(data)
            3L -> (track.compressionSettings ?: ByteArray(0)) + data
            else -> null
        }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Blocks without a duration last until the next line starts (capped at 10 seconds). */
    private fun fillMissingEnds(list: List<Cue>): List<Cue> {
        val sorted = list.sortedBy { it.start }
        return sorted.mapIndexed { i, cue ->
            if (!cue.end.isNaN()) cue
            else {
                val next = sorted.getOrNull(i + 1)?.start ?: (cue.start + 5)
                cue.copy(end = minOf(next, cue.start + 10))
            }
        }
    }

    /** Small buffered reader over a FileChannel with cheap seeking. */
    private class Reader(private val channel: FileChannel) {
        val size: Long = channel.size()
        private val buffer: ByteBuffer = ByteBuffer.allocate(8192)
        private var bufferStart = 0L
        private var bufferLength = 0
        var position = 0L

        fun readByte(): Int {
            if (position < bufferStart || position >= bufferStart + bufferLength) fill()
            if (bufferLength <= 0) throw IllegalStateException("unexpected end of file")
            val b = buffer.get((position - bufferStart).toInt()).toInt() and 0xFF
            position++
            return b
        }

        private fun fill() {
            buffer.clear()
            bufferStart = position
            var read = 0
            while (buffer.hasRemaining()) {
                val n = channel.read(buffer, bufferStart + read)
                if (n <= 0) break
                read += n
            }
            bufferLength = read
        }

        fun skip(n: Long) {
            position += n
        }

        /** Element ID, keeping the length marker bits as Matroska IDs are defined. */
        fun readId(): Long? {
            if (position >= size) return null
            val first = readByte()
            val length = Integer.numberOfLeadingZeros(first) - 23
            if (length !in 1..4) return null
            var value = first.toLong()
            repeat(length - 1) { value = (value shl 8) or readByte().toLong() }
            return value
        }

        /** Variable-size integer with the marker removed; -1 for "unknown size". */
        fun readSize(): Long {
            val first = readByte()
            val length = Integer.numberOfLeadingZeros(first) - 23
            if (length !in 1..8) throw IllegalStateException("invalid element size")
            var value = (first and (0xFF shr length)).toLong()
            var allOnes = value == (0xFF shr length).toLong()
            repeat(length - 1) {
                val b = readByte()
                if (b != 0xFF) allOnes = false
                value = (value shl 8) or b.toLong()
            }
            return if (allOnes) -1 else value
        }

        fun readUInt(n: Long): Long {
            var value = 0L
            repeat(n.toInt()) { value = (value shl 8) or readByte().toLong() }
            return value
        }

        fun readInt16(): Int {
            val value = (readByte() shl 8) or readByte()
            return value.toShort().toInt()
        }

        fun readBytes(n: Long): ByteArray {
            val out = ByteArray(n.toInt())
            for (i in out.indices) out[i] = readByte().toByte()
            return out
        }

        fun readString(n: Long): String = String(readBytes(n), Charsets.UTF_8).trimEnd(' ')
    }

    companion object {
        private val TEXT_CODECS = setOf("S_TEXT/UTF8", "S_TEXT/ASS", "S_TEXT/SSA", "S_TEXT/WEBVTT")

        private const val ID_SEGMENT = 0x18538067L
        private const val ID_INFO = 0x1549A966L
        private const val ID_TIMESTAMP_SCALE = 0x2AD7B1L
        private const val ID_TRACKS = 0x1654AE6BL
        private const val ID_TRACK_ENTRY = 0xAEL
        private const val ID_TRACK_NUMBER = 0xD7L
        private const val ID_TRACK_TYPE = 0x83L
        private const val ID_CODEC_ID = 0x86L
        private const val ID_LANGUAGE = 0x22B59CL
        private const val ID_LANGUAGE_BCP47 = 0x22B59DL
        private const val ID_NAME = 0x536EL
        private const val ID_CONTENT_ENCODINGS = 0x6D80L
        private const val ID_CONTENT_ENCODING = 0x6240L
        private const val ID_CONTENT_COMPRESSION = 0x5034L
        private const val ID_CONTENT_COMP_ALGO = 0x4254L
        private const val ID_CONTENT_COMP_SETTINGS = 0x4255L
        private const val ID_CLUSTER = 0x1F43B675L
        private const val ID_CLUSTER_TIMESTAMP = 0xE7L
        private const val ID_SIMPLE_BLOCK = 0xA3L
        private const val ID_BLOCK_GROUP = 0xA0L
        private const val ID_BLOCK = 0xA1L
        private const val ID_BLOCK_DURATION = 0x9BL

        private val TOP_LEVEL_IDS = setOf(
            ID_CLUSTER, ID_INFO, ID_TRACKS,
            0x1C53BB6BL, // Cues
            0x1254C367L, // Tags
            0x1043A770L, // Chapters
            0x1941A469L, // Attachments
            0x114D9B74L, // SeekHead
        )
    }
}
