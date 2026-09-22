package io.github.immersionplayer.subs

object SrtParser {
    private val timing = Regex(
        """(\d+):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun parse(content: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val lines = content.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val match = timing.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val g = match.groupValues
            val start = toSeconds(g[1], g[2], g[3], g[4])
            val end = toSeconds(g[5], g[6], g[7], g[8])
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[i])
                i++
            }
            cues.add(Cue(start, end, stripSrtTags(text.toString())))
        }
        return cues.normalized()
    }

    fun parseBlockText(text: String): String = stripSrtTags(text)

    private fun toSeconds(h: String, m: String, s: String, ms: String): Double =
        h.toInt() * 3600 + m.toInt() * 60 + s.toInt() + ms.padEnd(3, '0').toInt() / 1000.0

    private fun stripSrtTags(text: String): String =
        text.replace(Regex("""</?[a-zA-Z][^>]*>"""), "")
            .replace(Regex("""\{\\[^}]*\}"""), "")
}

object AssParser {
    /** Parses a complete .ass/.ssa file. */
    fun parse(content: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        var inEvents = false
        var format = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (rawLine in content.removePrefix("﻿").lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                format = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue

            val fields = line.substringAfter(':').split(',', limit = format.size)
            if (fields.size < format.size) continue
            val start = parseTime(fields[format.indexOf("start")].trim()) ?: continue
            val end = parseTime(fields[format.indexOf("end")].trim()) ?: continue
            val text = cleanText(fields[format.indexOf("text")]) ?: continue
            cues.add(Cue(start, end, text))
        }
        return cues.normalized()
    }

    /**
     * Text of an ASS event stored in a Matroska block:
     * "ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text".
     */
    fun parseBlockText(blockText: String): String? {
        val fields = blockText.split(',', limit = 9)
        if (fields.size < 9) return null
        return cleanText(fields[8])
    }

    /** Strips override tags. Returns null for vector drawings, which are not text. */
    fun cleanText(text: String): String? {
        if (Regex("""\\p[1-9]""").containsMatchIn(text)) return null
        return text.replace(Regex("""\{[^}]*\}"""), "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .trim()
            .ifEmpty { null }
    }

    private fun parseTime(value: String): Double? {
        val parts = value.split(':')
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return h * 3600 + m * 60 + s
    }
}
