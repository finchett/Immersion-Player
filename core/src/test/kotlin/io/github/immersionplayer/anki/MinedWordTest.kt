package io.github.immersionplayer.anki

import io.github.immersionplayer.dictionary.Definition
import io.github.immersionplayer.dictionary.GlossaryHtml
import io.github.immersionplayer.dictionary.TermEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MinedWordTest {
    private val entry = TermEntry(
        expression = "初日",
        reading = "しょにち",
        sourceLength = 2,
        reasons = emptyList(),
        definitions = listOf(
            Definition("JMdict", """["first day","opening day"]""", "n", "⭐"),
            Definition("JMdict", """["premiere"]""", "n 2", ""),
            Definition("JMdict", """["初日"]""", "forms", ""),
            Definition("Other", """["something else"]""", "", ""),
        ),
        frequencies = emptyList(),
        pitchPositions = listOf(0),
    )

    private val word = MinedWord(
        sentence = "初日はこんなもんでしょ",
        wordStart = 0,
        wordLength = 2,
        entry = entry,
        translation = "This is the way things are on the first day right?",
        videoName = "K-ON!! EP19",
        start = 143.765,
        end = 145.345,
        audioFile = "clip.ogg",
        imageFile = "clip.avif",
    )

    @Test fun sentencesPlusFields() {
        val fields = word.fields(CardFormats.JapaneseSentencesPlus)
        assertEquals("<b>初日</b>はこんなもんでしょ", fields["SentKanji"])
        assertEquals("", fields["SentFurigana"])
        assertEquals("[sound:clip.ogg]", fields["SentAudio"])
        assertEquals("初日", fields["VocabKanji"])
        assertEquals("初日[しょにち]", fields["VocabFurigana"])
        assertEquals("<img alt=\"snapshot\" src=\"clip.avif\">", fields["Image"])
        assertEquals("K-ON!! EP19 (02m23s765ms)", fields["Notes"])
        assertEquals(
            "<ol><li><i>(n)</i> first day; opening day</li><li><i>(n)</i> premiere</li></ol>",
            fields["VocabDef"],
        )
    }

    @Test fun shortDefinition() {
        assertEquals("first day, opening day or premiere", word.field(CardSource.ShortDefinition))
        val jitendex = entry.copy(definitions = listOf(Definition("Jitendex", """[{"type":"structured-content","content":[
            {"tag":"span","data":{"content":"part-of-speech-info"},"content":"noun"},
            {"tag":"ul","data":{"content":"glossary"},"content":[
                {"tag":"li","content":"(to be) eaten"},{"tag":"li","content":"food"},{"tag":"li","content":"Food."}]},
            {"tag":"div","data":{"content":"example-sentence"},"content":"ignored"}
        ]}]""", "", "")))
        assertEquals("eaten or food", word.copy(entry = jitendex).shortDefinition())
    }

    @Test fun boldInTheMiddle() {
        val line = word.copy(sentence = "それより衣装って全部そろった？", wordStart = 4, wordLength = 2)
        assertEquals("それより<b>衣装</b>って全部そろった？", line.field(CardSource.SentenceBold))
    }

    @Test fun mediaNames() {
        assertEquals("K-ON_EP19_02m23s765ms_02m25s345ms", word.mediaBaseName())
        assertEquals("1h00m01s500ms", MinedWord.timestamp(3601.5))
    }

    @Test fun furigana() {
        assertEquals("初日[しょにち]", Furigana.anki("初日", "しょにち"))
        assertEquals("食[た]べる", Furigana.anki("食べる", "たべる"))
        assertEquals("お 茶[ちゃ]", Furigana.anki("お茶", "おちゃ"))
        assertEquals("引[ひ]き 出[だ]す", Furigana.anki("引き出す", "ひきだす"))
        assertEquals("すごい", Furigana.anki("すごい", "すごい"))
        assertEquals("テレビ", Furigana.anki("テレビ", "てれび"))
    }

    @Test fun pitchGraphs() {
        assertEquals(listOf("ショ", "ニ", "チ"), PitchAccent.morae("ショニチ"))
        assertEquals(
            "<span style=\"box-shadow: inset -2px -2px 0 0px #FF6633;\">ショ</span>" +
                "<span style=\"box-shadow: inset 0px 2px 0 0px #FF6633;\">ニチ</span> <span class=\"pitch_number\">0</span>",
            PitchAccent.html("しょにち", 0),
        )
        assertEquals(
            "<span style=\"box-shadow: inset -2px 2px 0 0px #FF6633;\">イ</span>" +
                "<span style=\"box-shadow: inset 0px -2px 0 0px #FF6633;\">ショウ</span> <span class=\"pitch_number\">1</span>",
            PitchAccent.html("いしょう", 1),
        )
        // odaka: high to the end, then the drop onto the particle
        assertEquals(listOf(false, true, true), PitchAccent.pattern(3, 3))
        assertEquals(listOf(false, true, false, false), PitchAccent.pattern(4, 2))
    }

    @Test fun structuredGlossary() {
        val json = """[{"type":"structured-content","content":[
            {"tag":"span","data":{"content":"tag"},"style":{"fontWeight":"bold","onClick":"x"},"content":"n"},
            {"tag":"a","href":"?query=x","content":"link"},
            {"tag":"img","path":"a.png"},
            "<&>"
        ]}]"""
        assertEquals(
            "<span data-sc-content=\"tag\" style=\"font-weight:bold\">n</span><span>link</span>&lt;&amp;&gt;",
            GlossaryHtml.render(json),
        )
    }

    @Test fun guessedFormat() {
        val preset = CardFormats.forNoteType("Japanese Sentences+", CardFormats.JapaneseSentencesPlus.fields.keys.toList())
        assertEquals(CardSource.SentenceBold, preset.fields["SentKanji"])
        assertEquals("Japanese Sentences+", preset.noteType)

        val guessed = CardFormats.forNoteType(
            "Mining",
            listOf("Word", "Reading", "Sentence", "SentenceAudio", "Picture", "Meaning", "WordAudio", "PitchPosition"),
        ).fields
        assertEquals(CardSource.Word, guessed["Word"])
        assertEquals(CardSource.Reading, guessed["Reading"])
        assertEquals(CardSource.SentenceBold, guessed["Sentence"])
        assertEquals(CardSource.SentenceAudio, guessed["SentenceAudio"])
        assertEquals(CardSource.Screenshot, guessed["Picture"])
        assertEquals(CardSource.Definition, guessed["Meaning"])
        assertEquals(CardSource.None, guessed["WordAudio"])
        assertEquals(CardSource.PitchNumber, guessed["PitchPosition"])
    }
}
