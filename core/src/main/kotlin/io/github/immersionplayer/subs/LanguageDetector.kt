package io.github.immersionplayer.subs

/**
 * Guesses a subtitle track's language from its text, for files whose tags are wrong.
 * Each line votes by script (Japanese, Korean, Chinese, Russian, or Latin); Latin-script
 * languages are then told apart by counting a handful of very common words, which is weaker,
 * so tags win there.
 */
object LanguageDetector {

    private val STOPWORDS = mapOf(
        "en" to setOf("the", "you", "and", "is", "it", "to", "what", "that", "this", "i'm", "don't", "of"),
        "es" to setOf("que", "el", "los", "las", "es", "por", "qué", "pero", "una", "está", "y", "no"),
        "fr" to setOf("le", "les", "et", "est", "je", "vous", "pas", "une", "des", "c'est", "qu'il", "tu"),
        "de" to setOf("der", "die", "und", "ist", "nicht", "ich", "das", "du", "ein", "zu", "was", "wir"),
        "pt" to setOf("que", "não", "você", "uma", "os", "é", "eu", "está", "com", "isso", "o", "do"),
        "it" to setOf("che", "non", "il", "è", "sono", "una", "di", "per", "questo", "cosa", "io", "ma"),
    )

    private const val SAMPLE_CUES = 300
    private const val MIN_LINES = 3
    private const val MAJORITY = 0.6
    private const val LATIN = "latin"

    /**
     * The language most of this track's lines are in, or null when there's too little text or no
     * clear majority. Voting per line keeps an English track with Japanese song lyrics English.
     */
    fun detect(track: SubtitleTrack): String? {
        val votes = HashMap<String, Int>()
        val latinText = StringBuilder()
        for (cue in track.cues.asSequence().take(SAMPLE_CUES)) {
            val script = scriptOf(cue.text) ?: continue
            votes.merge(script, 1, Int::plus)
            if (script == LATIN) latinText.append(cue.text).append('\n')
        }
        val total = votes.values.sum()
        if (total < MIN_LINES) return null
        val (winner, count) = votes.maxByOrNull { it.value } ?: return null
        if (count < total * MAJORITY) return null
        return if (winner == LATIN) latinLanguage(latinText.toString()) else winner
    }

    /** The language of one piece of text, by script alone (Latin-script text is [LATIN]). */
    fun detect(text: String): String? = when (val script = scriptOf(text)) {
        LATIN -> latinLanguage(text)
        else -> script
    }

    private fun scriptOf(text: String): String? {
        var kana = 0
        var han = 0
        var hangul = 0
        var cyrillic = 0
        var latin = 0
        for (c in text) {
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        val letters = kana + han + hangul + cyrillic + latin
        if (letters < 2) return null
        return when {
            // Japanese always mixes in kana
            kana > 0 && kana + han >= latin -> "ja"
            hangul * 2 > letters -> "ko"
            han * 2 > letters -> "zh"
            cyrillic * 2 > letters -> "ru"
            latin * 2 > letters -> LATIN
            else -> null
        }
    }

    private fun latinLanguage(text: String): String {
        val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        val scores = STOPWORDS.mapValues { (_, stop) -> words.count { it in stop } }
        return scores.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: "en"
    }

    private val NON_LATIN = setOf("ja", "zh", "ko", "ru")

    /**
     * The language to treat [track] as: its tag, unless the text is clearly in another script
     * (a "jpn" tag on English lines) or Japanese/Chinese are confused (kana decides).
     */
    fun effectiveLanguage(track: SubtitleTrack, tagCode: String?): String? {
        val detected = detect(track) ?: return tagCode
        if (tagCode == null) return detected
        val tagLatin = tagCode !in NON_LATIN
        val detectedLatin = detected !in NON_LATIN
        return when {
            tagLatin != detectedLatin -> detected
            !tagLatin && tagCode != detected -> detected
            else -> tagCode
        }
    }
}
