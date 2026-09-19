package io.github.immersionplayer.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLookupPositionsTest {
    @Test
    fun prefersFirstKanjiOutsideSpeakerNames() {
        val text = "（祐子）\nいろいろ やる気が出なくて"
        val positions = DictionaryLookup.defaultLookupPositions(text)
        assertEquals('気', text[positions.first()])
    }

    @Test
    fun skipsSpeakerNameAndReading() {
        val text = "（相生祐子(あいおいゆうこ)）ああ"
        val positions = DictionaryLookup.defaultLookupPositions(text)
        assertEquals('あ', text[positions.first()])
        assertEquals(text.length - 2, positions.first())
    }
}
