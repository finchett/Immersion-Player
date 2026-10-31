package app.mizune.subs

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Reads the text subtitle tracks (3GPP timed text, which ffmpeg calls mov_text) out of an MP4,
 * M4V or QuickTime file, fragmented or not.
 *
 * The sample tables say where each line is, so only the boxes leading to them and the lines
 * themselves are read; audio and video are skipped by seeking, and so are their sample tables.
 */
class Mp4SubtitleExtractor(private val channel: FileChannel) {

    private class Sample(val offset: Long, val size: Long, val time: Long, val duration: Long)

    private class Track(val id: Long) {
        var timescale = 0L
        var language: String? = null
        var name: String? = null
        var isText = false
        var emptyEdit = 0L // movie timescale: the edit list's delay before the track starts
        var mediaStart = 0L // media timescale: where the edit list starts playing it
        val samples = mutableListOf<Sample>()

        // fragmented files: the movie header's defaults, and where the last fragment left off
        var defaultDuration = 0L
        var defaultSize = 0L
        var nextTime = 0L
    }

    /** Sample tables of the track being read, until its samples are laid out. */
    private class Tables {
        var entryType = ""
        var durations = LongArray(0) // stts, as (count, delta) pairs
        var chunkRuns = LongArray(0) // stsc, as (first chunk, samples per chunk) pairs
        var sizes = LongArray(0)
        var uniformSize = 0L
        var sampleCount = 0
        var chunkOffsets = LongArray(0)
    }

    private val reader = Reader(channel)
    private var movieTimescale = 1L
    private val tracks = mutableMapOf<Long, Track>()
    private val chapterTracks = mutableSetOf<Long>()

    /** The text subtitle tracks. */
    fun extract(): List<SubtitleTrack> {
        reader.position = 0
        boxes(reader.size) { type, start, end ->
            when (type) {
                "moov" -> {
                    parseMoov(end)
                    if (textTracks().isEmpty()) return emptyList()
                }
                "moof" -> parseMoof(start, end)
            }
        }
        return textTracks().sortedBy { it.id }.map { track ->
            // seconds added to every time by the edit list
            val shift = track.emptyEdit.toDouble() / movieTimescale - track.mediaStart.toDouble() / track.timescale
            val cues = track.samples.mapNotNull { sample ->
                val text = readLine(sample) ?: return@mapNotNull null
                val start = sample.time.toDouble() / track.timescale + shift
                Cue(start, start + sample.duration.toDouble() / track.timescale, text)
            }
            SubtitleTrack(track.name ?: "Track ${track.id}", track.language, cues.normalized())
        }
    }

    // QuickTime chapter titles are text tracks too; a tref "chap" from another track marks them
    private fun textTracks() = tracks.values.filter { it.isText && it.timescale > 0 && it.id !in chapterTracks }

    /** Calls [visit] with each box's type, start and end within [end], leaving the reader after it. */
    private inline fun boxes(end: Long, visit: (type: String, start: Long, end: Long) -> Unit) {
        while (reader.position + 8 <= end) {
            val start = reader.position
            var size = reader.readU32()
            val type = reader.readType()
            if (size == 1L) size = reader.readU64() else if (size == 0L) size = end - start
            if (size < 8) return
            val boxEnd = minOf(start + size, end)
            visit(type, start, boxEnd)
            reader.position = boxEnd
        }
    }

    private fun parseMoov(end: Long) = boxes(end) { type, _, boxEnd ->
        when (type) {
            "mvhd" -> {
                val version = reader.readU8()
                reader.skip(if (version == 1) 3 + 16L else 3 + 8L)
                movieTimescale = reader.readU32().coerceAtLeast(1)
            }
            "trak" -> parseTrak(boxEnd)
            "mvex" -> boxes(boxEnd) { child, _, _ ->
                if (child == "trex") {
                    reader.skip(4)
                    val track = tracks[reader.readU32()]
                    reader.skip(4) // sample description index
                    track?.defaultDuration = reader.readU32()
                    track?.defaultSize = reader.readU32()
                }
            }
        }
    }

    private fun parseTrak(end: Long) {
        val start = reader.position
        // the track's ID comes first, in tkhd, and everything else belongs to it
        var track: Track? = null
        boxes(end) { type, _, _ ->
            if (type == "tkhd") {
                val version = reader.readU8()
                reader.skip(if (version == 1) 3 + 16L else 3 + 8L)
                track = Track(reader.readU32())
            }
        }
        val current = track ?: return
        tracks[current.id] = current
        reader.position = start
        boxes(end) { type, _, boxEnd ->
            when (type) {
                "tref" -> boxes(boxEnd) { child, _, childEnd ->
                    if (child == "chap") while (reader.position + 4 <= childEnd) chapterTracks += reader.readU32()
                }
                "edts" -> boxes(boxEnd) { child, _, _ -> if (child == "elst") parseEditList(current) }
                "mdia" -> parseMdia(boxEnd, current)
                "udta" -> boxes(boxEnd) { child, _, childEnd ->
                    if (child == "name") current.name = reader.readString(childEnd - reader.position).ifBlank { null }
                }
            }
        }
    }

    private fun parseEditList(track: Track) {
        val version = reader.readU8()
        reader.skip(3)
        repeat(reader.readU32().toInt()) {
            val duration = if (version == 1) reader.readU64() else reader.readU32()
            val mediaTime = if (version == 1) reader.readU64() else reader.readU32().toInt().toLong()
            reader.skip(4) // rate
            if (mediaTime == -1L) {
                track.emptyEdit += duration
            } else {
                track.mediaStart = mediaTime
                return
            }
        }
    }

    private fun parseMdia(end: Long, track: Track) = boxes(end) { type, _, boxEnd ->
        when (type) {
            "mdhd" -> {
                val version = reader.readU8()
                reader.skip(if (version == 1) 3 + 16L else 3 + 8L)
                track.timescale = reader.readU32()
                reader.skip(if (version == 1) 8 else 4)
                if (track.language == null) track.language = mdhdLanguage(reader.readU16())
            }
            // the full BCP 47 tag, when there is one
            "elng" -> {
                reader.skip(4)
                track.language = reader.readString(boxEnd - reader.position).ifBlank { null } ?: track.language
            }
            "hdlr" -> if (track.name == null) {
                reader.skip(4 + 4 + 4 + 12)
                track.name = handlerName(reader.readBytes(boxEnd - reader.position))
            }
            "minf" -> boxes(boxEnd) { child, _, childEnd -> if (child == "stbl") parseStbl(childEnd, track) }
        }
    }

    private fun parseStbl(end: Long, track: Track) {
        val tables = Tables()
        boxes(end) { type, _, _ ->
            when (type) {
                "stsd" -> {
                    reader.skip(4 + 4 + 4)
                    tables.entryType = reader.readType()
                    // a video's tables are large, and no use here
                    if (tables.entryType !in TEXT_ENTRIES) return
                }
                "stts" -> tables.durations = readTable(2)
                "stsc" -> tables.chunkRuns = readTable(3).filterIndexed { i, _ -> i % 3 != 2 }.toLongArray()
                "stsz" -> {
                    reader.skip(4)
                    tables.uniformSize = reader.readU32()
                    tables.sampleCount = reader.readU32().toInt()
                    if (tables.uniformSize == 0L) tables.sizes = LongArray(tables.sampleCount) { reader.readU32() }
                }
                "stco" -> tables.chunkOffsets = readTable(1)
                "co64" -> {
                    reader.skip(4)
                    tables.chunkOffsets = LongArray(reader.readU32().toInt()) { reader.readU64() }
                }
            }
        }
        if (tables.entryType !in TEXT_ENTRIES) return
        track.isText = true
        track.samples += layOut(tables)
    }

    /** A full box's table of [columns] 32-bit values per entry, flattened. */
    private fun readTable(columns: Int): LongArray {
        reader.skip(4)
        return LongArray(reader.readU32().toInt() * columns) { reader.readU32() }
    }

    /** Where each sample is and when it plays, from the chunk, size and timing tables. */
    private fun layOut(tables: Tables): List<Sample> {
        val count = tables.sampleCount
        val durations = LongArray(count)
        var filled = 0
        for (i in tables.durations.indices step 2) {
            repeat(tables.durations[i].toInt()) { if (filled < count) durations[filled++] = tables.durations[i + 1] }
        }
        val samples = ArrayList<Sample>(count)
        var time = 0L
        var run = 0
        for (chunk in tables.chunkOffsets.indices) {
            // stsc numbers chunks from 1
            while (run + 2 < tables.chunkRuns.size && tables.chunkRuns[run + 2] <= chunk + 1) run += 2
            val perChunk = tables.chunkRuns.getOrNull(run + 1) ?: 0L
            var offset = tables.chunkOffsets[chunk]
            repeat(perChunk.toInt()) {
                val index = samples.size
                if (index >= count) return samples
                val size = if (tables.uniformSize != 0L) tables.uniformSize else tables.sizes[index]
                samples += Sample(offset, size, time, durations[index])
                offset += size
                time += durations[index]
            }
        }
        return samples
    }

    private fun parseMoof(moofStart: Long, end: Long) {
        // data with no offset of its own follows on from the last run's
        var implicitOffset = moofStart
        boxes(end) { type, _, trafEnd ->
            if (type != "traf") return@boxes
            var track: Track? = null
            var base = implicitOffset
            var defaultDuration = 0L
            var defaultSize = 0L
            boxes(trafEnd) { box, _, _ ->
                when (box) {
                    "tfhd" -> {
                        val flags = reader.readU32() and 0xFFFFFF
                        track = tracks[reader.readU32()]?.takeIf { it.isText }
                        if (track == null) return@boxes
                        base = when {
                            flags and 0x1 != 0L -> reader.readU64()
                            flags and 0x20000 != 0L -> moofStart
                            else -> implicitOffset
                        }
                        if (flags and 0x2 != 0L) reader.skip(4) // sample description index
                        defaultDuration = if (flags and 0x8 != 0L) reader.readU32() else track!!.defaultDuration
                        defaultSize = if (flags and 0x10 != 0L) reader.readU32() else track!!.defaultSize
                    }
                    "tfdt" -> track?.let {
                        val version = reader.readU8()
                        reader.skip(3)
                        it.nextTime = if (version == 1) reader.readU64() else reader.readU32()
                    }
                    "trun" -> track?.let {
                        val flags = reader.readU32() and 0xFFFFFF
                        val count = reader.readU32().toInt()
                        var offset = if (flags and 0x1 != 0L) base + reader.readU32().toInt() else implicitOffset
                        if (flags and 0x4 != 0L) reader.skip(4) // first sample's flags
                        repeat(count) { _ ->
                            val duration = if (flags and 0x100 != 0L) reader.readU32() else defaultDuration
                            val size = if (flags and 0x200 != 0L) reader.readU32() else defaultSize
                            if (flags and 0x400 != 0L) reader.skip(4)
                            if (flags and 0x800 != 0L) reader.skip(4)
                            it.samples += Sample(offset, size, it.nextTime, duration)
                            offset += size
                            it.nextTime += duration
                        }
                        implicitOffset = offset
                    }
                }
            }
        }
    }

    /** A timed-text sample: its length in two bytes, the text, then style boxes this doesn't need. */
    private fun readLine(sample: Sample): String? {
        if (sample.size < 2) return null
        reader.position = sample.offset
        val length = minOf(reader.readU16().toLong(), sample.size - 2)
        if (length <= 0) return null
        val bytes = reader.readBytes(length)
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }
        val text = String(bytes, charset).removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return SrtParser.parseBlockText(text)
    }

    /** mdhd's language: three letters packed in 15 bits, or an old Macintosh language code. */
    private fun mdhdLanguage(code: Int): String? = when {
        code == 0x7FFF -> null
        code < 0x400 -> MAC_LANGUAGES[code]
        else -> String(CharArray(3) { i -> (((code shr (10 - 5 * i)) and 0x1F) + 0x60).toChar() }).takeIf { it.all { c -> c in 'a'..'z' } }
    }

    /** hdlr's name, unless it only says what kind of track this is ("SubtitleHandler"). */
    private fun handlerName(bytes: ByteArray): String? {
        // QuickTime writes a length byte first, MP4 a terminating zero
        val raw = if (bytes.isNotEmpty() && bytes[0].toInt() and 0xFF == bytes.size - 1) bytes.copyOfRange(1, bytes.size) else bytes
        val name = String(raw, Charsets.UTF_8).substringBefore('\u0000').trim()
        return name.takeUnless { it.isEmpty() || it.endsWith("Handler") || it in GENERIC_HANDLER_NAMES }
    }

    /** Small buffered reader over a FileChannel with cheap seeking. */
    private class Reader(private val channel: FileChannel) {
        val size: Long = channel.size()
        private val buffer: ByteBuffer = ByteBuffer.allocate(8192)
        private var bufferStart = 0L
        private var bufferLength = 0
        var position = 0L

        fun readU8(): Int {
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

        fun readU16(): Int = (readU8() shl 8) or readU8()

        fun readU32(): Long = (readU16().toLong() shl 16) or readU16().toLong()

        fun readU64(): Long = (readU32() shl 32) or readU32()

        fun readType(): String = String(CharArray(4) { readU8().toChar() })

        fun readBytes(n: Long): ByteArray {
            val out = ByteArray(n.toInt().coerceAtLeast(0))
            for (i in out.indices) out[i] = readU8().toByte()
            return out
        }

        fun readString(n: Long): String = String(readBytes(n), Charsets.UTF_8).substringBefore('\u0000').trim()
    }

    companion object {
        // "tx3g" in MP4, "text" in QuickTime; the samples are laid out the same
        private val TEXT_ENTRIES = setOf("tx3g", "text")

        private val GENERIC_HANDLER_NAMES = setOf("Core Media Text", "Apple Text Media Handler")

        /** The Macintosh language codes QuickTime files may carry instead of ISO 639. */
        private val MAC_LANGUAGES = mapOf(
            0 to "eng", 1 to "fra", 2 to "deu", 3 to "ita", 4 to "nld", 5 to "swe", 6 to "spa", 7 to "dan",
            8 to "por", 9 to "nor", 10 to "heb", 11 to "jpn", 12 to "ara", 13 to "fin", 14 to "ell", 15 to "isl",
            16 to "mlt", 17 to "tur", 18 to "hrv", 19 to "zh-Hant", 20 to "urd", 21 to "hin", 22 to "tha",
            23 to "kor", 24 to "lit", 25 to "pol", 26 to "hun", 27 to "est", 28 to "lav", 31 to "fas",
            32 to "rus", 33 to "zh-Hans", 37 to "ron", 38 to "ces", 39 to "slk", 40 to "slv", 42 to "srp",
            43 to "mkd", 44 to "bul", 45 to "ukr", 80 to "vie", 81 to "ind",
        )
    }
}
