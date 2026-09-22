package io.github.immersionplayer.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageDetectorTest {
    private fun track(language: String?, vararg lines: String) =
        SubtitleTrack("t", language, lines.mapIndexed { i, text -> Cue(i * 2.0, i * 2.0 + 1, text) })

    private val japanese = arrayOf("今日は何を食べたい？", "食べさせられなかった。", "勉強しましょう。", "そうですね、行きましょうか。")
    private val english = arrayOf("What do you want to eat today?", "I couldn't make them eat.", "Let's study.", "Is that what you meant?")
    private val spanish = arrayOf("¿Qué quieres comer hoy?", "No pude hacer que comieran.", "Pero eso no es lo que dijo el.", "Los chicos están aquí.")
    private val chinese = arrayOf("你今天想吃什么？", "我们一起学习吧。", "这是我的朋友。", "他们已经到了北京。")

    @Test
    fun scriptSettlesNonLatinLanguages() {
        assertEquals("ja", LanguageDetector.detect(track(null, *japanese)))
        assertEquals("zh", LanguageDetector.detect(track(null, *chinese)))
        assertEquals("en", LanguageDetector.detect(track(null, *english)))
        assertEquals("es", LanguageDetector.detect(track(null, *spanish)))
    }

    @Test
    fun englishWithJapaneseLyricsStaysEnglish() {
        // a signs-and-songs track: mostly English, some lines of Japanese lyrics
        val signs = track("eng", *english, *english, "君の声が聞こえる", "空へ飛んでいく")
        assertEquals("en", SubtitleFiles.effectiveLanguage(signs))
        val dialogue = track("jpn", *japanese)
        assertEquals(dialogue, SubtitleFiles.pickPrimary(listOf(signs, dialogue), "ja"))
    }

    @Test
    fun tooLittleTextIsUnknown() {
        assertNull(LanguageDetector.detect(track(null, "♪～", "はい")))
    }

    @Test
    fun wrongTagsAreOverruledAcrossScripts() {
        val japaneseTaggedEnglish = track("eng", *japanese)
        val englishTaggedJapanese = track("jpn", *english)
        assertEquals(japaneseTaggedEnglish, SubtitleFiles.pickPrimary(listOf(englishTaggedJapanese, japaneseTaggedEnglish), "ja"))
        assertEquals(englishTaggedJapanese, SubtitleFiles.pickSecondary(listOf(englishTaggedJapanese, japaneseTaggedEnglish), japaneseTaggedEnglish, "en"))
    }

    @Test
    fun latinTagsWinOverStopwordGuesses() {
        // stopword counting is weak; a Latin-script tag on Latin-script text is kept
        assertEquals("es", LanguageDetector.effectiveLanguage(track("spa", *english), "es"))
    }

    @Test
    fun untaggedTracksAreDetected() {
        assertEquals("ja", SubtitleFiles.effectiveLanguage(track(null, *japanese)))
    }
}
