package app.mizune.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Subtitle files as subtitle sites hand them out, in their languages' old code pages (`src/test/resources/encodings`). */
class SubtitleEncodingTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/encodings/$name")!!.readBytes()

    /** [name] read as a sidecar, with no hints but its own name. */
    private fun firstLine(name: String, languages: List<String> = emptyList()): String {
        val track = SubtitleFiles.parseSidecar(name, SubtitleEncoding.decode(bytes(name), name, languages))!!
        assertEquals(name, 8, track.cues.size)
        return track.cues.first().text
    }

    @Test
    fun arabicInWindows1256() {
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-1256.ar.srt"))
        // no tag in the name: told from its letters
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-1256.srt"))
        // nor from ASS's ASCII headers
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-1256.ass"))
    }

    @Test
    fun byteOrderMarksAndUtf16() {
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-utf16le-bom.srt"))
        assertEquals("今日は何を食べたい？", firstLine("japanese-utf16be-bom.srt"))
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-utf16le.srt"))
        // the mark itself isn't text
        assertEquals('1', SubtitleEncoding.decode(bytes("arabic-utf8-bom.srt"), "arabic-utf8-bom.srt").first())
    }

    @Test
    fun utf8StaysUtf8() {
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-utf8.srt"))
        assertEquals("今日は何を食べたい？", firstLine("japanese-utf8.srt"))
        // even when a hint names another code page
        assertEquals("今日は何を食べたい？", firstLine("japanese-utf8.srt", listOf("ar")))
    }

    @Test
    fun japaneseInShiftJis() {
        assertEquals("今日は何を食べたい？", firstLine("japanese-sjis.srt"))
        assertEquals("今日は何を食べたい？", firstLine("japanese-sjis.srt", listOf("ja", "en")))
    }

    @Test
    fun otherCodePagesWithoutATag() {
        assertEquals("Привет, как дела сегодня?", firstLine("russian-1251.srt"))
        assertEquals("你今天想吃什么？", firstLine("chinese-gbk.srt"))
        assertEquals("오늘 뭐 먹고 싶어?", firstLine("korean-euckr.srt"))
        assertEquals("שלום, מה שלומך היום?", firstLine("hebrew-1255.srt"))
        assertEquals("Γεια σου, τι κάνεις σήμερα;", firstLine("greek-1253.srt"))
        assertEquals("สวัสดี วันนี้เป็นอย่างไรบ้าง", firstLine("thai-874.srt"))
        assertEquals("Salut, ça va aujourd'hui ?", firstLine("french-1252.srt"))
        assertEquals("Cześć, jak się dziś masz?", firstLine("polish-1250.pl.srt"))
    }

    @Test
    fun hintsDontOverrideWhatTheBytesSay() {
        // a Japanese learner peeking in Arabic, and a Russian file: the Russian still wins
        assertEquals("Привет, как дела сегодня?", firstLine("russian-1251.srt", listOf("ja", "ar")))
        assertEquals("مرحبا، كيف حالك اليوم؟", firstLine("arabic-1256.srt", listOf("ja", "ru")))
    }

    @Test
    fun whateverTheUserReadsTheBytesDecide() {
        val expected = mapOf(
            "arabic-1256.srt" to "مرحبا، كيف حالك اليوم؟",
            "japanese-sjis.srt" to "今日は何を食べたい？",
            "russian-1251.srt" to "Привет, как дела сегодня?",
            "chinese-gbk.srt" to "你今天想吃什么？",
            "chinese-big5.srt" to "你今天想吃什麼？",
            "korean-euckr.srt" to "오늘 뭐 먹고 싶어?",
            "hebrew-1255.srt" to "שלום, מה שלומך היום?",
            "greek-1253.srt" to "Γεια σου, τι κάνεις σήμερα;",
            "thai-874.srt" to "สวัสดี วันนี้เป็นอย่างไรบ้าง",
            "french-1252.srt" to "Salut, ça va aujourd'hui ?",
        )
        val hints = listOf("ja", "zh", "ko", "ar", "ru", "he", "el", "th", "pl", "tr", "en")
        for ((name, line) in expected) {
            for (hint in hints) assertEquals("$name with $hint", line, firstLine(name, listOf("ja", hint)))
        }
    }

    @Test
    fun plainAsciiIsUntouched() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        assertEquals(text, SubtitleEncoding.decode(text.toByteArray(Charsets.US_ASCII), "a.srt"))
        assertTrue(SubtitleEncoding.decode(ByteArray(0), "a.srt").isEmpty())
    }
}
