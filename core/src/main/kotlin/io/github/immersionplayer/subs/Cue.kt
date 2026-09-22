package io.github.immersionplayer.subs

/** A single subtitle line. Times are in seconds. */
data class Cue(
    val start: Double,
    val end: Double,
    val text: String,
)

/** A parsed subtitle track, with cues sorted by start time. */
data class SubtitleTrack(
    val name: String,
    val language: String?,
    val cues: List<Cue>,
) {
    /**
     * Index of the cue that is showing at [time], or else the last cue that started before it.
     * Returns -1 when [time] is before the first cue.
     */
    fun indexAt(time: Double): Int {
        var lo = 0
        var hi = cues.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].start <= time) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** Cue that is actually on screen at [time], if any. */
    fun activeIndexAt(time: Double): Int {
        val index = indexAt(time)
        if (index < 0) return -1
        return if (time < cues[index].end) index else -1
    }
}

internal fun List<Cue>.normalized(): List<Cue> =
    asSequence()
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotEmpty() && it.end > it.start }
        .sortedBy { it.start }
        // typeset subtitles often repeat the same line in several layers
        .fold(mutableListOf<Cue>()) { acc, cue ->
            val last = acc.lastOrNull()
            if (last != null && last.text == cue.text && cue.start <= last.end + 0.05) {
                acc[acc.size - 1] = last.copy(end = maxOf(last.end, cue.end))
            } else {
                acc.add(cue)
            }
            acc
        }

/** Text of this track overlapping [start]..[end], e.g. the English for a Japanese line. */
fun SubtitleTrack.translationFor(start: Double, end: Double): String? {
    val lines = cues.filter { it.start < end - 0.1 && it.end > start + 0.1 }
    return lines.joinToString("\n") { it.text }.ifEmpty { null }
}
