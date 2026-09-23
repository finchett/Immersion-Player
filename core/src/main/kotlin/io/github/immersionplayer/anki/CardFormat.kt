package io.github.immersionplayer.anki

import org.json.JSONObject

/** What goes into one field of a card. */
enum class CardSource(val label: String) {
    None("Nothing"),
    Sentence("Sentence"),
    SentenceBold("Sentence, word in bold"),
    Translation("Translation (peek language)"),
    SentenceAudio("Sentence audio"),
    Screenshot("Screenshot"),
    Word("Word"),
    WordFurigana("Word with reading, as furigana"),
    Reading("Reading"),
    WordAsSeen("Word as it appears in the line"),
    PitchAccent("Pitch accent graph"),
    PitchNumber("Pitch accent number"),
    Definition("Definition, first dictionary"),
    ShortDefinition("Definition, short: a, b or c"),
    AllDefinitions("Definitions, all dictionaries"),
    Source("Episode and time"),
}

/** A note type and what fills each of its fields. Fields not listed are left empty. */
data class CardFormat(val noteType: String, val fields: Map<String, CardSource>) {

    fun toJson(): JSONObject = JSONObject(fields.mapValues { it.value.name })

    companion object {
        fun fromJson(noteType: String, json: JSONObject): CardFormat = CardFormat(
            noteType,
            json.keySet().associateWith { key ->
                CardSource.entries.firstOrNull { it.name == json.optString(key) } ?: CardSource.None
            },
        )
    }
}

/** Card formats that are known field by field, and a best guess for any other note type. */
object CardFormats {

    /**
     * mpvacious's "Japanese sentences+" (Sentences+). SentFurigana and the pitch number stay
     * empty, as mpvacious leaves them: the AJT Japanese add-on fills them in when the note is added.
     */
    val JapaneseSentencesPlus = CardFormat(
        "Japanese sentences+",
        mapOf(
            "SentKanji" to CardSource.SentenceBold,
            "SentFurigana" to CardSource.None,
            "SentEng" to CardSource.Translation,
            "SentAudio" to CardSource.SentenceAudio,
            "VocabKanji" to CardSource.Word,
            "VocabFurigana" to CardSource.WordFurigana,
            "VocabPitchPattern" to CardSource.PitchAccent,
            "VocabPitchNum" to CardSource.None,
            "VocabDef" to CardSource.Definition,
            "VocabAudio" to CardSource.None,
            "Image" to CardSource.Screenshot,
            "Notes" to CardSource.Source,
            "MakeProductionCard" to CardSource.None,
            "Focus" to CardSource.None,
        ),
    )

    val presets = listOf(JapaneseSentencesPlus)

    /** A preset whose note type and fields match, otherwise a mapping guessed from field names. */
    fun forNoteType(noteType: String, fieldNames: List<String>): CardFormat {
        val preset = presets.firstOrNull {
            it.noteType.equals(noteType, ignoreCase = true) && it.fields.keys == fieldNames.toSet()
        }
        if (preset != null) return preset.copy(noteType = noteType)
        val used = mutableSetOf<CardSource>()
        return CardFormat(noteType, fieldNames.associateWith { name ->
            guess(name).takeIf { it == CardSource.None || used.add(it) } ?: CardSource.None
        })
    }

    private fun guess(field: String): CardSource {
        val name = field.lowercase()
        fun has(vararg words: String) = words.any { it in name }
        val sentence = has("sent", "example", "context")
        return when {
            has("audio", "sound") -> if (sentence) CardSource.SentenceAudio else CardSource.None
            has("image", "picture", "screenshot", "snapshot") -> CardSource.Screenshot
            has("pitch") -> if (has("num", "position")) CardSource.PitchNumber else CardSource.PitchAccent
            sentence && has("eng", "translation", "meaning") -> CardSource.Translation
            sentence && has("furigana", "reading") -> CardSource.None
            sentence -> CardSource.SentenceBold
            has("furigana") -> CardSource.WordFurigana
            has("reading", "kana") -> CardSource.Reading
            has("def", "meaning", "glossary", "english", "back") -> CardSource.Definition
            has("vocab", "word", "expression", "kanji", "front", "term") -> CardSource.Word
            has("note", "source", "misc") -> CardSource.Source
            else -> CardSource.None
        }
    }
}
