package io.github.immersionplayer.library

/**
 * Readable titles from file and folder names, by heuristics rather than a database: release
 * groups, checksums and encoding notes come off ("[Group] K-On! - 01 (1080p BD) [9F3A21BC].mkv"
 * is "K-On! - 01"), and in a folder of episodes the series name they share comes off too, so
 * each reads "Episode 1". Anything that doesn't look like those is left as it was.
 */
object Titles {
    // encoding notes that can't be mistaken for words in a title, in any case
    private val technical = Regex(
        """(?<![A-Za-z0-9])(?:\d{3,4}[pi]|4k|bd-?rip|bd-?remux|blu-?ray|remux|web-?dl|web-?rip|hdtv(?:rip)?|dvd-?rip|""" +
            """tv-?rip|x26[45]|h[ .]?26[45]|hevc|xvid|1[02]-?bits?|8-?bits?|hi10p?|flac|truehd|dual[- ]?audio|""" +
            """multi[- ]?subs?)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )

    // short ones that are only notes when written in capitals ("Charlotte's Web" keeps its web)
    private val technicalCaps = Regex(
        """(?<![A-Za-z0-9])(?:WEB|BD|DVD|AVC|AV1|AAC(?:[ .]?2[ .]0)?|OPUS|DTS(?:-HD)?|E?AC-?3|DDP?(?:[ .]?[257][ .][01])?|""" +
            """HDR(?:10\+?)?|UHD|CHS|CHT|BIG5|RAW)(?![A-Za-z0-9])""",
    )

    // what a parenthesis holds when it's about the release rather than the show
    private val releaseWords = Regex("""\b(?:complete|batch|uncensored|raw|subs?|subbed|dub|dubbed|fin)\b""", RegexOption.IGNORE_CASE)
    private val checksum = Regex("""^[0-9A-Fa-f]{8}$""")
    private val brackets = Regex("""\[[^\]]*]|【[^】]*】|\{[^}]*}""")
    private val parentheses = Regex("""\(([^)]*)\)""")
    private val version = Regex("""(?<=\d)v\d(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
    private val season = Regex("""(?<![A-Za-z0-9])S(\d{1,2})(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE)
    private val edges = Regex("""^[\s\-–—~_.,:|+]+|[\s\-–—~_.,:|+]+$""")

    private fun isTechnical(text: String) =
        technical.containsMatchIn(text) || technicalCaps.containsMatchIn(text) || checksum.matches(text.trim())

    /** [name] without its video extension or release tags; the name itself if nothing would be left. */
    fun clean(name: String): String {
        val base = if (isVideoName(name)) name.substringBeforeLast('.') else name
        var text = brackets.replace(base, " ")
        text = parentheses.replace(text) { match ->
            val inside = match.groupValues[1]
            if (isTechnical(inside) || releaseWords.containsMatchIn(inside) || inside.isBlank()) " " else match.value
        }
        // scene names use dots and underscores for spaces
        text = text.replace('_', ' ')
        if (' ' !in text.trim() && text.count { it == '.' } >= 2) text = text.replace('.', ' ')
        text = cutAtFirstNote(text)
        text = version.replace(text, "")
        text = season.replace(text) { "Season ${it.groupValues[1].toInt()}" }
        text = tidy(text)
        if (text.isNotEmpty()) return text

        // a name made only of brackets: the first is the group, notes and checksums go, the rest is the title
        val parts = brackets.findAll(base).map { it.value.drop(1).dropLast(1).trim() }
            .filter { it.isNotEmpty() && !isTechnical(it) }.toList()
        val kept = if (parts.size > 1) parts.drop(1) else parts
        return tidy(kept.joinToString(" ")).ifEmpty { tidy(base).ifEmpty { base } }
    }

    /** Everything from the first encoding note on goes ("Show 01 1080p WEB H264-GROUP" is "Show 01"). */
    private fun cutAtFirstNote(text: String): String {
        var rest = text
        while (true) {
            val first = listOfNotNull(technical.find(rest), technicalCaps.find(rest)).minByOrNull { it.range.first }
                ?: return rest
            if (rest.substring(0, first.range.first).isNotBlank()) return rest.substring(0, first.range.first)
            rest = rest.substring(first.range.last + 1) // a note before anything else: drop just the note
        }
    }

    private fun tidy(text: String) = edges.replace(text.replace(Regex("""\s+"""), " "), "")

    // words that number a series of their own: "Lesson 1" stays "Lesson 1", not "Episode 1"
    private val counters = setOf(
        "lesson", "lecture", "chapter", "part", "track", "unit", "day", "week", "vol", "volume", "class", "session",
    )

    private class Episode(val season: Int?, val number: Int, val title: String)

    private val seasonEpisode = Regex("""^S(\d{1,2})\s*E(\d{1,4})(.*)$""", RegexOption.IGNORE_CASE)
    private val numbered = Regex("""^(?:(?:ep(?:isode)?|e)\.?\s*|#)?(\d{1,4})(?![0-9])(.*)$""", RegexOption.IGNORE_CASE)
    private val japanese = Regex("""^第\s*(\d{1,4})\s*[話话集回](.*)$""")
    private val ending = Regex("""^\s*(?:end|final)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    private fun episode(remainder: String): Episode? {
        val text = remainder.trim()
        seasonEpisode.matchEntire(text)?.let { m ->
            return titled(m.groupValues[3])?.let { Episode(m.groupValues[1].toInt(), m.groupValues[2].toInt(), it) }
        }
        (japanese.matchEntire(text) ?: numbered.matchEntire(text))?.let { m ->
            return titled(m.groupValues[2])?.let { Episode(null, m.groupValues[1].toInt(), it) }
        }
        return null
    }

    /** What follows an episode number, as its title: nothing, or words after a separator. Null if it runs on. */
    private fun titled(rest: String): String? {
        val text = ending.replace(rest, "")
        if (text.isNotEmpty() && text[0].isLetterOrDigit()) return null
        return tidy(text)
    }

    /**
     * Titles for the videos in one folder, given their file names, in the same order: cleaned,
     * and when they're episodes of one series, just which episode each is.
     */
    fun episodes(names: List<String>): List<String> {
        val cleaned = names.map(::clean)
        if (cleaned.size < 2) return cleaned
        val shared = sharedPrefix(cleaned)
        if (shared.trim().lowercase().trimEnd('.') in counters) return cleaned
        val parsed = cleaned.map { episode(it.substring(shared.length)) }
        if (parsed.any { it == null }) return cleaned
        val seasons = parsed.mapNotNull { it!!.season }.toSet()
        return parsed.map { e ->
            val label = if (seasons.size > 1) "S${e!!.season ?: 1} E${e.number}" else "Episode ${e!!.number}"
            if (e.title.isEmpty()) label else "$label · ${e.title}"
        }
    }

    /** The start every name shares, back to the last word break, so a shared "0" of "01" and "02" stays. */
    private fun sharedPrefix(names: List<String>): String {
        var length = names.minOf { it.length }
        for (name in names) {
            var i = 0
            while (i < length && name[i] == names[0][i]) i++
            length = i
        }
        while (length > 0 && !names[0][length - 1].let { it.isWhitespace() || it in "-–—_.~:" }) length--
        return names[0].substring(0, length)
    }
}
