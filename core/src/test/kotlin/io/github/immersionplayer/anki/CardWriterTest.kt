package io.github.immersionplayer.anki

import io.github.immersionplayer.dictionary.Definition
import io.github.immersionplayer.dictionary.TermEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CardWriterTest {
    /** A collection in memory: one deck, the Sentences+ note type, notes as field maps. */
    private class FakeAnki : AnkiBackend {
        val notes = mutableMapOf<Long, Map<String, String>>()
        val media = mutableListOf<String>()
        override fun noteTypes() = listOf("Japanese sentences+")
        override fun fieldNames(noteType: String) =
            if (noteType == "Japanese sentences+") CardFormats.JapaneseSentencesPlus.fields.keys.toList() else emptyList()
        override fun decks() = listOf("Mining")
        override fun findNotes(deck: String, field: String, value: String) =
            notes.filterValues { it[field] == value }.keys.toList()
        override fun storeMedia(file: File): String = file.name.also { media += it }
        override fun addNote(deck: String, noteType: String, fields: Map<String, String>, tags: List<String>): Long =
            (notes.size + 1L).also { notes[it] = fields }
        override fun deleteNotes(ids: List<Long>) { ids.forEach(notes::remove) }
    }

    private val word = MinedWord(
        sentence = "初日はこんなもんでしょ", wordStart = 0, wordLength = 2,
        entry = TermEntry("初日", "しょにち", 2, emptyList(), listOf(Definition("JMdict", """["first day"]""", "n", "")), emptyList(), emptyList()),
        translation = null, videoName = "Show 01", start = 10.0, end = 12.0,
    )

    private class Media(val audioWorks: Boolean) : CardMedia {
        var made = 0
        override val imageExtension = "webp"
        override fun audio(word: MinedWord, out: File) = audioWorks.also { if (it) { out.writeText("ogg"); made++ } }
        override fun image(word: MinedWord, out: File) = true.also { out.writeText("img"); made++ }
    }

    private val dir: File = Files.createTempDirectory("cards").toFile()
    private val target = CardTarget(CardFormats.JapaneseSentencesPlus, "Mining", listOf("immersion-player"))

    @Test fun addsWithMediaThenRefusesTheDuplicateBeforeMakingMedia() {
        val anki = FakeAnki()
        val writer = CardWriter(anki, dir)
        val added = writer.add(word, target, Media(audioWorks = true))
        assertEquals(emptyList<String>(), added.missing)
        assertEquals("[sound:Show_01_00m10s000ms_00m12s000ms.ogg]", anki.notes.getValue(added.noteId)["SentAudio"])
        assertEquals("<img alt=\"snapshot\" src=\"Show_01_00m10s000ms_00m12s000ms.webp\">", anki.notes.getValue(added.noteId)["Image"])
        assertTrue("temporary media are cleaned up", dir.listFiles().isNullOrEmpty())

        val again = Media(audioWorks = true)
        try {
            writer.add(word, target, again)
            fail("duplicate accepted")
        } catch (e: CardWriter.Duplicate) {
            assertEquals(added.noteId, e.noteId)
        }
        assertEquals("no media made for a duplicate", 0, again.made)

        writer.remove(added.noteId)
        assertTrue(anki.notes.isEmpty())
    }

    @Test fun missingAudioStillAddsTheCard() {
        val anki = FakeAnki()
        val added = CardWriter(anki, dir).add(word, target, Media(audioWorks = false))
        assertEquals(listOf("audio"), added.missing)
        assertEquals("", anki.notes.getValue(added.noteId)["SentAudio"])
    }

    @Test fun missingDeckIsExplained() {
        try {
            CardWriter(FakeAnki(), dir).add(word, CardTarget(target.format, "Nope", emptyList()), Media(true))
            fail("added to a missing deck")
        } catch (e: AnkiException) {
            assertEquals("Deck \"Nope\" isn't in Anki", e.message)
        }
    }

    @Test fun searchTermsAreLiteral() {
        assertEquals("\"SentKanji:a\\*b\\_c\\\"d\"", AnkiBackend.quote("SentKanji:a*b_c\"d"))
    }
}
