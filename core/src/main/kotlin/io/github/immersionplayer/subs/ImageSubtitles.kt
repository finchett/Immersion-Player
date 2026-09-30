package io.github.immersionplayer.subs

import org.json.JSONArray
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/** One Matroska block of a PGS track: a display set's segments, and when it shows. */
class PgsBlock(val start: Double, val end: Double, val data: ByteArray)

/** An image subtitle track (Blu-ray PGS): its lines' timing is known at once, their text only by OCR. */
class ImageSubtitleTrack(val name: String, val language: String?, val blocks: List<PgsBlock>) {
    internal val lines: List<PgsLine> by lazy { PgsDecoder.lines(blocks) }

    /** How many lines it shows, without decoding any pictures. */
    val lineCount: Int get() = lines.size

    /** Line [index] as a picture, ready for OCR. */
    fun image(index: Int): SubtitleImage? = PgsDecoder.render(blocks, lines[index])
}

/**
 * A subtitle picture, ready for OCR: 8-bit grey, dark text on white, one byte per pixel
 * ([width] bytes a row). The outline most subtitles have merges into the background.
 */
class SubtitleImage(val width: Int, val height: Int, val pixels: ByteArray)

/** Reads the text in one [SubtitleImage]; set up for one language. Used from one thread. */
interface TextRecognizer : AutoCloseable {
    fun read(image: SubtitleImage): String
    override fun close() {}
}

/** Where a picture track's text is kept between viewings. */
interface LineStore {
    /** The text read so far, one entry a line (null for unread), or null if nothing is stored. */
    fun load(): List<String?>?
    fun save(texts: List<String?>)
}

/** A [LineStore] in a JSON file. */
class FileLineStore(private val file: File) : LineStore {
    override fun load(): List<String?>? = runCatching {
        if (!file.isFile) return null
        val array = JSONArray(file.readText())
        List(array.length()) { if (array.isNull(it)) null else array.getString(it) }
    }.getOrNull()

    override fun save(texts: List<String?>) {
        val array = JSONArray()
        texts.forEach { array.put(it ?: org.json.JSONObject.NULL) }
        val partial = File(file.path + ".part")
        partial.writeText(array.toString())
        partial.renameTo(file)
    }
}

/**
 * The text of a picture track, read a line at a time on a background thread: the lines asked for
 * with [want] first (the one on screen and a few after it), then the rest in order from there, so
 * after one viewing the whole track is read and stored. [cues] is a live view: a line's text is ""
 * until it has been read, and [onRead] is told as each one is.
 */
class PictureLines(
    private val track: ImageSubtitleTrack,
    private val newRecognizer: () -> TextRecognizer,
    private val store: LineStore? = null,
) : AutoCloseable {
    private val lines = track.lines
    private val japanese = SubtitleFiles.codeForTag(track.language) == "ja"
    private val texts = AtomicReferenceArray<String?>(lines.size)
    private val read = AtomicInteger()
    private val urgent = LinkedBlockingDeque<Int>()
    @Volatile private var fillFrom = 0
    @Volatile private var lastWanted = -1
    @Volatile private var running = true
    private var worker: Thread? = null

    /** Called on the reading thread with each line's index once its text is in [cues]. */
    @Volatile var onRead: ((Int) -> Unit)? = null

    init {
        store?.load()?.takeIf { it.size == lines.size }?.forEachIndexed { i, text ->
            if (text != null) { texts.set(i, text); read.incrementAndGet() }
        }
    }

    val cues: List<Cue> = object : AbstractList<Cue>() {
        override val size get() = lines.size
        override fun get(index: Int) = lines[index].let { Cue(it.start, it.end, texts.get(index) ?: "") }
    }

    val readCount: Int get() = read.get()
    val isComplete: Boolean get() = read.get() == lines.size

    /** Starts reading in the background, from the first line until something is [want]ed. */
    @Synchronized
    fun start() {
        if (worker != null || isComplete) return
        worker = Thread(::work, "ocr ${track.name}").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    /** Reads line [index] and the [ahead] after it before anything else. Cheap to call often. */
    fun want(index: Int, ahead: Int = LOOKAHEAD) {
        if (index < 0 || index == lastWanted) return
        lastWanted = index
        fillFrom = index
        urgent.clear()
        for (i in index..minOf(index + ahead, lines.lastIndex)) if (texts.get(i) == null) urgent.add(i)
    }

    /** [want] for the line showing at [time], or the next to come. */
    fun wantAt(time: Double) {
        val i = lines.indexOfFirst { it.end > time }
        if (i >= 0) want(i)
    }

    override fun close() {
        running = false
        worker?.interrupt()
    }

    private fun work() {
        val recognizer = runCatching(newRecognizer).getOrElse { return }
        try {
            var sinceSave = 0
            while (running) {
                val next = urgent.pollFirst() ?: nextUnread() ?: break
                if (texts.get(next) != null) continue
                val text = runCatching { track.image(next)?.let { ImageSubtitles.clean(recognizer.read(it), japanese) } }
                    .getOrNull().orEmpty()
                texts.set(next, text)
                read.incrementAndGet()
                onRead?.invoke(next)
                if (++sinceSave >= SAVE_EVERY) { save(); sinceSave = 0 }
            }
        } finally {
            recognizer.close()
            save()
        }
    }

    /** The first unread line from where the viewer is, wrapping round to the start. */
    private fun nextUnread(): Int? {
        val from = fillFrom.coerceIn(0, maxOf(lines.size - 1, 0))
        for (i in from until lines.size) if (texts.get(i) == null) return i
        for (i in 0 until from) if (texts.get(i) == null) return i
        return null
    }

    private fun save() {
        runCatching { store?.save(List(lines.size) { texts.get(it) }) }
    }

    companion object {
        /** Lines read ahead of the one on screen, so they're ready before they show. */
        const val LOOKAHEAD = 4
        private const val SAVE_EVERY = 25
    }
}

object ImageSubtitles {
    /** Languages OCR models are bundled for, as Tesseract names them. */
    private val MODELS = mapOf("en" to "eng", "ja" to "jpn")

    /** The OCR model for a language code ("ja" -> "jpn"), or null if none is bundled. */
    fun model(code: String): String? = MODELS[code]

    /** The model for [track]'s language, or null if none is bundled. */
    fun model(track: ImageSubtitleTrack): String? = SubtitleFiles.codeForTag(track.language)?.let(::model)

    /**
     * The image tracks worth reading: in one of [languages] (the target and peek language), with a
     * bundled model, and in a language no text track already covers.
     */
    fun tracksToRead(text: List<SubtitleTrack>, images: List<ImageSubtitleTrack>, languages: List<String>) =
        images.filter { image ->
            val code = SubtitleFiles.codeForTag(image.language) ?: return@filter false
            code in languages && model(code) != null && image.lineCount > 0 &&
                text.none { SubtitleFiles.isLanguage(it, code) }
        }

    /** [track] as a subtitle track whose text fills in as [PictureLines] reads it; call [PictureLines.start]. */
    fun open(track: ImageSubtitleTrack, newRecognizer: () -> TextRecognizer, store: LineStore?): SubtitleTrack {
        val lines = PictureLines(track, newRecognizer, store)
        return SubtitleTrack("${track.name} (OCR)", track.language, lines.cues, lines)
    }

    private val LONE_BAR = Regex("(?<![^\\s\"(\\[-])\\|(?=[\\s'’,.!?]|$)")

    private val CJK = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}ー・、。「」『』！？…（）]")

    /** Tidies OCR output: no blank lines, and none of the spaces Tesseract puts between Japanese characters. */
    fun clean(text: String, japanese: Boolean): String = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n") { line ->
            // a lone "|" is Tesseract's usual misreading of the word "I"
            if (!japanese) line.replace(Regex("\\s+"), " ").replace(LONE_BAR, "I")
            else buildString {
                line.forEachIndexed { i, c ->
                    val drop = c.isWhitespace() &&
                        (CJK.matches(line.getOrElse(i - 1) { ' ' }.toString()) || CJK.matches(line.getOrElse(i + 1) { ' ' }.toString()))
                    if (!drop) append(c)
                }
            }
        }
}

/** A line of a PGS track: when it shows, and the blocks that draw it (replayed from [epochStart]). */
internal class PgsLine(val start: Double, var end: Double, val epochStart: Int, val block: Int)

/**
 * Blu-ray PGS: each display set is a run of segments (composition, window, palette, object,
 * end), and objects are run-length-encoded pictures indexed into a YCbCr+alpha palette. Objects
 * and palettes last for an "epoch", so drawing a line replays the blocks since its epoch began.
 */
internal object PgsDecoder {
    private const val PDS = 0x14
    private const val ODS = 0x15
    private const val PCS = 0x16

    /** Pixels of white margin around each picture; OCR reads text touching the edge badly. */
    private const val MARGIN = 12

    private class Placement(val objectId: Int, val x: Int, val y: Int, val crop: IntArray?)

    private class PgsObject(var width: Int = 0, var height: Int = 0, val rle: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream())

    /** What a block's composition says: [placements] empty means it clears the screen. */
    private class Composition(val epochStart: Boolean, val paletteId: Int, val placements: List<Placement>)

    private class State {
        val palettes = HashMap<Int, IntArray>()
        val objects = HashMap<Int, PgsObject>()
        /** A fingerprint of each object's pixels, to tell a redraw of the same line from a new one. */
        val objectHashes = HashMap<Int, Int>()
    }

    /**
     * The lines a track shows, from composition timing alone. A display set that puts the same
     * pictures in the same place as the line before it (a fade, a palette change) extends that line.
     */
    fun lines(blocks: List<PgsBlock>): List<PgsLine> {
        val state = State()
        val lines = mutableListOf<PgsLine>()
        var epochStart = 0
        var lastKey: String? = null
        for ((index, block) in blocks.withIndex()) {
            val composition = apply(block.data, state, decodeObjects = false) ?: continue
            if (composition.epochStart) epochStart = index
            val last = lines.lastOrNull()
            if (composition.placements.isEmpty()) {
                if (last != null) last.end = minOf(last.end, block.start)
                lastKey = null
                continue
            }
            // on screen until the next display set replaces or clears it
            val next = blocks.getOrNull(index + 1)?.start ?: (block.start + 5)
            val end = minOf(if (block.end.isNaN()) next else minOf(block.end, next), block.start + 10)
            val key = composition.placements.joinToString(";") { "${it.objectId}@${it.x},${it.y}:${state.objectHashes[it.objectId]}" }
            if (last != null && key == lastKey && block.start - last.end < 0.25) {
                last.end = maxOf(last.end, end)
            } else {
                lines += PgsLine(block.start, end, epochStart, index)
            }
            lastKey = key
        }
        return lines.filter { it.end > it.start }
    }

    /** Line [line] drawn as a picture, by replaying its epoch's blocks. */
    fun render(blocks: List<PgsBlock>, line: PgsLine): SubtitleImage? {
        val state = State()
        var composition: Composition? = null
        for (i in line.epochStart..line.block) {
            val c = apply(blocks[i].data, state, decodeObjects = true)
            if (i == line.block) composition = c
        }
        val c = composition ?: return null
        return draw(c.placements, state.objects, state.palettes[c.paletteId] ?: return null)
    }

    /** Applies one block's segments to [state]; returns its composition, if it has one. */
    private fun apply(data: ByteArray, state: State, decodeObjects: Boolean): Composition? {
        var composition: Composition? = null
        var pos = 0
        while (pos + 3 <= data.size) {
            val type = data[pos].toInt() and 0xFF
            val size = u16(data, pos + 1)
            val at = pos + 3
            if (at + size > data.size) break
            when (type) {
                PCS -> if (size >= 11) {
                    val epochStart = data[at + 7].toInt() and 0x80 != 0
                    if (epochStart) { state.palettes.clear(); state.objects.clear(); state.objectHashes.clear() }
                    val count = data[at + 10].toInt() and 0xFF
                    var p = at + 11
                    val placements = List(count) {
                        val objectId = u16(data, p)
                        val cropped = data[p + 3].toInt() and 0x80 != 0
                        val x = u16(data, p + 4)
                        val y = u16(data, p + 6)
                        val crop = if (cropped) intArrayOf(u16(data, p + 8), u16(data, p + 10), u16(data, p + 12), u16(data, p + 14)) else null
                        p += if (cropped) 16 else 8
                        Placement(objectId, x, y, crop)
                    }
                    composition = Composition(epochStart, data[at + 9].toInt() and 0xFF, placements)
                }
                PDS -> if (decodeObjects) {
                    val palette = state.palettes.getOrPut(data[at].toInt() and 0xFF) { IntArray(256) }
                    var p = at + 2
                    while (p + 5 <= at + size) {
                        val entry = data[p].toInt() and 0xFF
                        val luma = data[p + 1].toInt() and 0xFF
                        val alpha = data[p + 4].toInt() and 0xFF
                        palette[entry] = (luma shl 8) or alpha
                        p += 5
                    }
                }
                ODS -> {
                    val id = u16(data, at)
                    val first = data[at + 3].toInt() and 0x80 != 0
                    val from = if (first) at + 11 else at + 4
                    val payloadHash = data.copyOfRange(from, at + size).contentHashCode()
                    state.objectHashes[id] = if (first) payloadHash else 31 * (state.objectHashes[id] ?: 0) + payloadHash
                    if (decodeObjects) {
                        val obj = if (first) PgsObject().also { state.objects[id] = it } else state.objects[id]
                        if (obj != null) {
                            if (first) {
                                obj.width = u16(data, at + 7)
                                obj.height = u16(data, at + 9)
                            }
                            obj.rle.write(data, from, at + size - from)
                        }
                    }
                }
            }
            pos = at + size
        }
        return composition
    }

    private fun draw(placements: List<Placement>, objects: Map<Int, PgsObject>, palette: IntArray): SubtitleImage? {
        val shown = placements.mapNotNull { p -> objects[p.objectId]?.takeIf { it.width > 0 && it.height > 0 }?.let { p to it } }
        if (shown.isEmpty()) return null
        // each placement's visible rectangle, in screen coordinates
        val rects = shown.map { (p, obj) ->
            val c = p.crop
            if (c == null) intArrayOf(p.x, p.y, obj.width, obj.height) else intArrayOf(p.x, p.y, c[2], c[3])
        }
        val left = rects.minOf { it[0] }
        val top = rects.minOf { it[1] }
        val width = rects.maxOf { it[0] + it[2] } - left + MARGIN * 2
        val height = rects.maxOf { it[1] + it[3] } - top + MARGIN * 2
        if (width > 8000 || height > 8000) return null
        val out = ByteArray(width * height) { 0xFF.toByte() }
        shown.forEachIndexed { i, (p, obj) ->
            val indices = decodeRle(obj.rle.toByteArray(), obj.width, obj.height)
            val (cropX, cropY) = p.crop?.let { it[0] to it[1] } ?: (0 to 0)
            val rect = rects[i]
            for (y in 0 until rect[3]) {
                val sy = y + cropY
                if (sy >= obj.height) break
                val row = (rect[1] - top + MARGIN + y) * width + (rect[0] - left + MARGIN)
                for (x in 0 until rect[2]) {
                    val sx = x + cropX
                    if (sx >= obj.width) break
                    val entry = palette[indices[sy * obj.width + sx].toInt() and 0xFF]
                    val luma = ((((entry shr 8) and 0xFF) - 16) * 255 / 219).coerceIn(0, 255)
                    val onBlack = luma * (entry and 0xFF) / 255
                    out[row + x] = (255 - onBlack).toByte()
                }
            }
        }
        return SubtitleImage(width, height, out)
    }

    /** PGS run-length encoding into palette indices, [width] × [height]. */
    fun decodeRle(rle: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        var pos = 0
        var x = 0
        var y = 0
        fun next() = if (pos < rle.size) rle[pos++].toInt() and 0xFF else 0
        while (pos < rle.size && y < height) {
            val first = next()
            var run: Int
            var color = 0
            if (first != 0) {
                run = 1
                color = first
            } else {
                val flags = next()
                if (flags == 0) { // end of line
                    x = 0
                    y++
                    continue
                }
                run = flags and 0x3F
                if (flags and 0x40 != 0) run = (run shl 8) or next()
                if (flags and 0x80 != 0) color = next()
            }
            val base = y * width
            repeat(run) {
                if (x < width) out[base + x] = color.toByte()
                x++
            }
        }
        return out
    }

    private fun u16(data: ByteArray, at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
}
