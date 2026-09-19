package io.github.immersionplayer.dictionary

import org.json.JSONArray
import org.json.JSONObject

/** One dictionary's definition of a term. */
data class Definition(
    val dictionaryTitle: String,
    val glossaryJson: String,
    val definitionTags: String,
    val termTags: String,
)

data class Frequency(val dictionaryTitle: String, val display: String, val rank: Double)

/** A headword with all matching dictionaries' definitions. */
data class TermEntry(
    val expression: String,
    val reading: String,
    val sourceLength: Int,
    val reasons: List<String>,
    val definitions: List<Definition>,
    val frequencies: List<Frequency>,
    val pitchPositions: List<Int>,
)

data class LookupResult(
    val text: String,
    val start: Int,
    /** Length of the longest match, for highlighting. */
    val matchLength: Int,
    val entries: List<TermEntry>,
)

class DictionaryLookup(private val database: DictionaryDatabase) {

    /** Longest dictionary match starting at [start], at most [maxLength] characters long. */
    fun lookup(text: String, start: Int, maxLength: Int = MAX_LENGTH): LookupResult? {
        if (start !in text.indices) return null
        val window = scanWindow(text, start, maxLength.coerceIn(1, MAX_LENGTH))
        if (window.isEmpty()) return null

        // candidates for every prefix length, longest first
        val candidatesByLength = (window.length downTo 1).associateWith { length ->
            val source = window.substring(0, length)
            val variants = linkedSetOf(source, katakanaToHiragana(source))
            variants.flatMap { Deinflector.deinflect(it) }
        }
        val keys = candidatesByLength.values.flatten().mapTo(hashSetOf()) { it.term }
        val rows = database.findTerms(keys)
        if (rows.isEmpty()) return LookupResult(text, start, 0, emptyList())

        val titles = database.dictionaryTitles()
        val priorities = database.dictionaries().associate { it.id to it.priority }
        val rowsByKey = HashMap<String, MutableList<TermRow>>()
        for (row in rows) {
            rowsByKey.getOrPut(row.expression) { mutableListOf() }.add(row)
            if (row.reading != row.expression) rowsByKey.getOrPut(row.reading) { mutableListOf() }.add(row)
        }

        data class Match(val row: TermRow, val length: Int, val reasons: List<String>, val exact: Boolean)

        val matches = mutableListOf<Match>()
        val seenRows = hashSetOf<TermRow>()
        for ((length, candidates) in candidatesByLength) {
            for (candidate in candidates) {
                for (row in rowsByKey[candidate.term].orEmpty()) {
                    if (!Deinflector.matches(candidate, Deinflector.entryRules(row.rules))) continue
                    if (!seenRows.add(row)) continue
                    matches.add(Match(row, length, candidate.reasons, row.expression == candidate.term))
                }
            }
        }
        if (matches.isEmpty()) return LookupResult(text, start, 0, emptyList())

        val meta = database.findMeta(matches.mapTo(hashSetOf()) { it.row.expression })
        val metaByExpression = meta.groupBy { it.second }

        val grouped = matches.groupBy { it.row.expression to it.row.reading }
        val entries = grouped.map { (key, group) ->
            val (expression, reading) = key
            val best = group.maxWith(compareBy<Match> { it.length }.thenBy { it.exact })
            val definitions = group
                .sortedWith(compareBy<Match> { priorities[it.row.dictionaryId] ?: 0 }.thenByDescending { it.row.score })
                .map {
                    Definition(
                        dictionaryTitle = titles[it.row.dictionaryId] ?: "?",
                        glossaryJson = it.row.glossaryJson,
                        definitionTags = it.row.definitionTags,
                        termTags = it.row.termTags,
                    )
                }
            val entryMeta = metaByExpression[expression].orEmpty()
            TermEntry(
                expression = expression,
                reading = reading,
                sourceLength = best.length,
                reasons = best.reasons,
                definitions = definitions,
                frequencies = entryMeta.filter { it.third.first == "freq" }
                    .mapNotNull { parseFrequency(titles[it.first] ?: "?", it.third.second, reading) },
                pitchPositions = entryMeta.filter { it.third.first == "pitch" }
                    .flatMap { parsePitch(it.third.second, reading) }
                    .distinct(),
            ) to best
        }.sortedWith(
            compareByDescending<Pair<TermEntry, Match>> { it.first.sourceLength }
                .thenByDescending { it.second.exact }
                .thenBy { it.first.frequencies.minOfOrNull { f -> f.rank } ?: Double.MAX_VALUE }
                .thenByDescending { it.second.row.score }
        ).map { it.first }

        return LookupResult(text, start, entries.first().sourceLength, entries)
    }

    private fun scanWindow(text: String, start: Int, maxLength: Int): String {
        val end = minOf(text.length, start + maxLength)
        val builder = StringBuilder()
        for (i in start until end) {
            val c = text[i]
            if (c in STOP_CHARACTERS || c.isWhitespace()) break
            builder.append(c)
        }
        return builder.toString()
    }

    /** Frequency data can be a number, a string, {value, displayValue}, or {reading, frequency}. */
    private fun parseFrequency(title: String, data: String, reading: String): Frequency? {
        val value: Any = runCatching { JSONObject(data) }.getOrNull() ?: data
        if (value is JSONObject && value.has("reading")) {
            if (value.optString("reading") != reading) return null
            return frequencyValue(title, value.opt("frequency"))
        }
        return frequencyValue(title, value)
    }

    private fun frequencyValue(title: String, value: Any?): Frequency? =
        when (value) {
            is Number -> Frequency(title, value.toString().removeSuffix(".0"), value.toDouble())
            is JSONObject -> {
                val rank = value.optDouble("value", Double.NaN)
                val display = value.optString("displayValue").ifEmpty { rank.toString().removeSuffix(".0") }
                if (rank.isNaN()) null else Frequency(title, display, rank)
            }
            is String -> {
                val rank = Regex("""\d+(\.\d+)?""").find(value)?.value?.toDoubleOrNull() ?: return null
                Frequency(title, value, rank)
            }
            else -> null
        }

    private fun parsePitch(data: String, reading: String): List<Int> {
        val obj = runCatching { JSONObject(data) }.getOrNull() ?: return emptyList()
        if (obj.optString("reading") != reading) return emptyList()
        val pitches = obj.optJSONArray("pitches") ?: JSONArray()
        return (0 until pitches.length()).mapNotNull { i ->
            pitches.optJSONObject(i)?.opt("position")?.let { (it as? Number)?.toInt() }
        }
    }

    companion object {
        private const val MAX_LENGTH = 24
        private const val STOP_CHARACTERS = "。、！？!?「」『』（）()【】［］[]〈〉《》…‥・,.，．\"'“”‘’〜～♪"

        fun isKanji(c: Char): Boolean = c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c == '々'

        /**
         * Where to look up by default in a line: kanji first, then other text, skipping
         * speaker names and readings in brackets, punctuation and spaces.
         */
        fun defaultLookupPositions(text: String): List<Int> {
            val candidates = mutableListOf<Int>()
            var depth = 0
            for ((i, c) in text.withIndex()) {
                when (c) {
                    '（', '(', '［', '[', '【', '〔' -> depth++
                    '）', ')', '］', ']', '】', '〕' -> depth = (depth - 1).coerceAtLeast(0)
                    else -> if (depth == 0 && !c.isWhitespace() && c !in STOP_CHARACTERS) candidates.add(i)
                }
            }
            return candidates.filter { isKanji(text[it]) } + candidates.filter { !isKanji(text[it]) }
        }

        fun katakanaToHiragana(text: String): String =
            buildString(text.length) {
                for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
            }
    }
}
