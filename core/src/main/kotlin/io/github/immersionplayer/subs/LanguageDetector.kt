package io.github.immersionplayer.subs

/**
 * Guesses a subtitle track's language from its text, for files whose tags are wrong.
 * Script settles Japanese, Korean, Chinese and Russian; Latin-script languages are told apart
 * by counting a handful of very common words, which is weaker, so tags win there.
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
    private const val MIN_LETTERS = 20

    /** The language code this text is written in, or null when there's too little to tell. */
    fun detect(track: SubtitleTrack): String? =
        detect(track.cues.asSequence().take(SAMPLE_CUES).joinToString("\n") { it.text })

    fun detect(text: String): String? {
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
        if (letters < MIN_LETTERS) return null
        return when {
            // Japanese always mixes in kana; a few percent is plenty
            kana > letters * 0.05 -> "ja"
            hangul > letters * 0.3 -> "ko"
            han > letters * 0.3 -> "zh"
            cyrillic > letters * 0.3 -> "ru"
            latin > letters * 0.5 -> latinLanguage(text)
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
