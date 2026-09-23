package io.github.immersionplayer.anki

import io.github.immersionplayer.dictionary.Definition
import io.github.immersionplayer.dictionary.DictionaryLookup
import io.github.immersionplayer.dictionary.GlossaryHtml
import io.github.immersionplayer.dictionary.TermEntry

/**
 * A word looked up in a subtitle line, with everything a card can be made of. Times are in
 * the video's clock (subtitle offset already applied). Media names are as stored in Anki.
 */
data class MinedWord(
    val sentence: String,
    /** Where the looked-up text starts in [sentence], and how long it is there. */
    val wordStart: Int,
    val wordLength: Int,
    val entry: TermEntry,
    val translation: String?,
    /** The video's name, without its extension. */
    val videoName: String,
    val start: Double,
    val end: Double,
    val audioFile: String? = null,
    val imageFile: String? = null,
) {
    /** The value of each field of [format], as HTML. */
    fun fields(format: CardFormat): Map<String, String> =
        format.fields.mapValues { (_, source) -> field(source) }

    fun field(source: CardSource): String = when (source) {
        CardSource.None -> ""
        CardSource.Sentence -> escape(sentence)
        CardSource.SentenceBold -> {
            val from = wordStart.coerceIn(0, sentence.length)
            val to = (wordStart + wordLength).coerceIn(from, sentence.length)
            escape(sentence.substring(0, from)) + "<b>" + escape(sentence.substring(from, to)) + "</b>" +
                escape(sentence.substring(to))
        }
        CardSource.Translation -> translation?.let(::escape).orEmpty()
        CardSource.SentenceAudio -> audioFile?.let { "[sound:$it]" }.orEmpty()
        CardSource.Screenshot -> imageFile?.let { "<img alt=\"snapshot\" src=\"${escape(it)}\">" }.orEmpty()
        CardSource.Word -> escape(entry.expression)
        CardSource.WordFurigana -> escape(Furigana.anki(entry.expression, entry.reading))
        CardSource.Reading -> escape(entry.reading)
        CardSource.WordAsSeen -> escape(sentence.substring(wordStart.coerceIn(0, sentence.length), (wordStart + wordLength).coerceIn(0, sentence.length)))
        CardSource.PitchAccent -> entry.pitchPositions.joinToString(", ") { PitchAccent.html(entry.reading, it) }
        CardSource.PitchNumber -> entry.pitchPositions.joinToString(",")
        CardSource.Definition -> entry.definitions.groupBy { it.dictionaryTitle }.values.firstOrNull()
            ?.let(::definitionsHtml).orEmpty()
        CardSource.ShortDefinition -> escape(shortDefinition())
        CardSource.AllDefinitions -> entry.definitions.groupBy { it.dictionaryTitle }.entries.joinToString("") { (title, list) ->
            "<div><i>(${escape(title)})</i></div>" + definitionsHtml(list)
        }
        CardSource.Source -> escape("$videoName (${timestamp(start)})")
    }

    /** "Show 01_02m23s765ms_02m25s345ms", mpvacious's naming, safe as a file name everywhere. */
    fun mediaBaseName(): String {
        val name = videoName.replace(Regex("""[\s　]+"""), "_").replace(Regex("""[^\p{L}\p{N}_\-]"""), "")
            .take(60).ifEmpty { "clip" }
        return "${name}_${timestamp(start)}_${timestamp(end)}"
    }

    /**
     * Up to three glosses from the first dictionary, the first sense's first: "a, b or c".
     * Bracketed notes ("(to be) ...", "(esp. ...)") are dropped, and repeats skipped.
     */
    fun shortDefinition(count: Int = 3): String {
        val senses = entry.definitions.groupBy { it.dictionaryTitle }.values.firstOrNull().orEmpty()
            .filter { "forms" !in it.definitionTags.split(' ') }
        val picked = LinkedHashMap<String, String>()
        for (sense in senses) {
            for (gloss in GlossaryHtml.glosses(sense.glossaryJson)) {
                val clean = gloss.replace(Regex("""\s*[(（][^)）]*[)）]\s*"""), " ").trim().trimEnd('.', ';')
                if (clean.isNotEmpty()) picked.putIfAbsent(clean.lowercase(), clean)
                if (picked.size == count) break
            }
            if (picked.size == count) break
        }
        val words = picked.values.toList()
        return when (words.size) {
            0, 1 -> words.joinToString()
            else -> words.dropLast(1).joinToString(", ") + " or " + words.last()
        }
    }

    private fun definitionsHtml(definitions: List<Definition>): String {
        // rikaitan's JMdict adds a row listing other spellings; it isn't a meaning
        val senses = definitions.filter { "forms" !in it.definitionTags.split(' ') }.map { definition ->
            val tags = definition.definitionTags.split(' ').filter { it.isNotBlank() && it.toIntOrNull() == null }.distinct()
            val prefix = if (tags.isEmpty()) "" else "<i>(${escape(tags.joinToString(", "))})</i> "
            prefix + GlossaryHtml.render(definition.glossaryJson)
        }
        return when (senses.size) {
            0 -> ""
            1 -> senses[0]
            else -> senses.joinToString("", "<ol>", "</ol>") { "<li>$it</li>" }
        }
    }

    private fun escape(text: String) = GlossaryHtml.escape(text)

    companion object {
        /** 02m23s765ms, or 1h02m23s765ms past the hour. */
        fun timestamp(seconds: Double): String {
            val ms = Math.round(seconds.coerceAtLeast(0.0) * 1000)
            val h = ms / 3_600_000
            val m = ms / 60_000 % 60
            val s = ms / 1000 % 60
            return (if (h > 0) "${h}h" else "") + "%02dm%02ds%03dms".format(m, s, ms % 1000)
        }
    }
}

/** Anki's furigana syntax: 食[た]べる, with the reading only over the kanji. */
object Furigana {

    fun anki(expression: String, reading: String): String {
        if (reading.isEmpty() || reading == expression) return expression
        // runs of kanji and of kana; the kana have to appear in the reading as they are
        val runs = Regex("""[^\p{IsHiragana}\p{IsKatakana}ー]+|[\p{IsHiragana}\p{IsKatakana}ー]+""").findAll(expression)
            .map { it.value }.toList()
        val pattern = runs.joinToString("", "^", "$") { run ->
            if (isKana(run)) Regex.escape(DictionaryLookup.katakanaToHiragana(run)) else "(.+?)"
        }
        val match = Regex(pattern).find(DictionaryLookup.katakanaToHiragana(reading))
            ?: return "$expression[$reading]"
        val out = StringBuilder()
        var group = 1
        for (run in runs) {
            if (isKana(run)) {
                out.append(run)
            } else {
                // the space ends the previous run, so the reading sits over this one only
                if (out.isNotEmpty()) out.append(' ')
                val range = match.groups[group++]!!.range
                out.append(run).append('[').append(reading.substring(range)).append(']')
            }
        }
        return out.toString()
    }

    private fun isKana(text: String) = text.all { it in 'ぁ'..'ゖ' || it in 'ァ'..'ヺ' || it == 'ー' }
}

/**
 * A pitch accent as the small graph Yomitan's cards use: a line over the high morae, under the
 * low ones, and a tick where the pitch changes, followed by the downstep number.
 */
object PitchAccent {
    private const val COLOR = "#FF6633"

    fun morae(kana: String): List<String> {
        val out = mutableListOf<String>()
        for (c in kana) {
            if (c in SMALL && out.isNotEmpty()) out[out.lastIndex] += c else out.add(c.toString())
        }
        return out
    }

    /** High (true) or low for each mora, for a downstep after mora [downstep] (0 = none). */
    fun pattern(count: Int, downstep: Int): List<Boolean> = (1..count).map { i ->
        when (downstep) {
            0 -> i > 1
            1 -> i == 1
            else -> i in 2..downstep
        }
    }

    fun html(reading: String, downstep: Int): String {
        val morae = morae(hiraganaToKatakana(reading))
        if (morae.isEmpty()) return ""
        val highs = pattern(morae.size, downstep)
        val out = StringBuilder()
        var i = 0
        while (i < morae.size) {
            var j = i
            while (j + 1 < morae.size && highs[j + 1] == highs[i]) j++
            // a tick on the right where the pitch changes, including the drop after an odaka word
            val changes = j + 1 < morae.size || (highs[i] && downstep == morae.size)
            val x = if (changes) "-2px" else "0px"
            val y = if (highs[i]) "2px" else "-2px"
            out.append("<span style=\"box-shadow: inset $x $y 0 0px $COLOR;\">")
                .append(morae.subList(i, j + 1).joinToString(""))
                .append("</span>")
            i = j + 1
        }
        return "$out <span class=\"pitch_number\">$downstep</span>"
    }

    private fun hiraganaToKatakana(text: String) =
        buildString(text.length) { for (c in text) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c) }

    private const val SMALL = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮ"
}
