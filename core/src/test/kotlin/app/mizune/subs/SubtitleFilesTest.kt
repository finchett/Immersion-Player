package app.mizune.subs

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFilesTest {
    private fun track(name: String, language: String?, vararg lines: String) =
        SubtitleTrack(name, language, lines.mapIndexed { i, text -> Cue(i * 2.0, i * 2.0 + 1, text) })

    private val japanese = arrayOf("今日は何を食べたい？", "食べさせられなかった。", "勉強しましょう。", "そうですね、行きましょうか。")
    private val english = arrayOf("What do you want to eat today?", "I couldn't make them eat.", "Let's study.", "Is that what you meant?")

    @Test
    fun embeddedEnglishPeeksUnderJapaneseFromAFile() {
        // Japanese beside the video, English (dialogue and signs) inside it
        val file = track("Episode 1.ja.srt", "ja", *japanese)
        val signs = track("Signs & Songs", "eng", *english)
        val dialogue = track("Full Subtitles", "eng", *english, *english)
        val tracks = SubtitleFiles.withEmbedded(listOf(file), listOf(signs, dialogue))
        assertEquals(listOf(file, signs, dialogue), tracks)

        val primary = SubtitleFiles.pickPrimary(tracks, "ja")
        assertEquals(file, primary)
        assertEquals(dialogue, SubtitleFiles.pickSecondary(tracks, primary, "en"))
    }

    @Test
    fun aFileWinsForItsLanguage() {
        // the file is there on purpose, even when the video's own Japanese track is fuller
        val file = track("Episode 1.srt", null, *japanese)
        val embedded = track("Track 3", "jpn", *japanese, *japanese)
        val english = track("Track 4", "eng", *english)
        val tracks = SubtitleFiles.withEmbedded(listOf(file), listOf(embedded, english))
        assertEquals(listOf(file, english), tracks)
        assertEquals(file, SubtitleFiles.pickPrimary(tracks, "ja"))
    }
}
