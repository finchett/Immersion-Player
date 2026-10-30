package app.mizune.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The peek line in any offered language, or none. */
class PeekLanguageTest {
    private fun track(name: String, language: String?, vararg lines: String) =
        SubtitleTrack(name, language, lines.mapIndexed { i, text -> Cue(i * 2.0, i * 2.0 + 1, text) })

    private fun srt(vararg lines: String) =
        lines.mapIndexed { i, text -> "${i + 1}\n00:00:0${i},000 --> 00:00:0${i},900\n$text\n" }.joinToString("\n")

    /** A few everyday lines in each language offered. */
    private val samples = mapOf(
        "ja" to arrayOf("今日は何を食べたい？", "一緒に勉強しましょう。", "これは私の友達です。", "もう着いたよ。"),
        "en" to arrayOf("What do you want to eat today?", "Let's study together.", "This is my friend.", "Is that what you meant?"),
        "zh" to arrayOf("你今天想吃什么？", "我们一起学习吧。", "这是我的朋友。", "他们已经到了。"),
        "ko" to arrayOf("오늘 뭐 먹고 싶어?", "같이 공부하자.", "이건 내 친구야.", "벌써 도착했어."),
        "es" to arrayOf("¿Qué quieres comer hoy?", "Estudiemos juntos, pero no aquí.", "Este es mi amigo.", "¿Es eso lo que querías decir?"),
        "fr" to arrayOf("Qu'est-ce que tu veux manger aujourd'hui ?", "Je ne sais pas.", "C'est mon ami.", "Vous êtes déjà là ?"),
        "de" to arrayOf("Was willst du heute essen?", "Ich weiß es nicht.", "Das ist mein Freund.", "Wir sind schon da."),
        "pt" to arrayOf("O que você quer comer hoje?", "Eu não sei.", "Isso é do meu amigo.", "Você está com fome?"),
        "it" to arrayOf("Cosa vuoi mangiare oggi?", "Non lo so, io.", "Questo è il mio amico.", "Sono già qui, ma per poco."),
        "ru" to arrayOf("Что ты хочешь съесть сегодня?", "Давай учиться вместе.", "Это мой друг.", "Мы уже здесь, вы тоже?"),
        "uk" to arrayOf("Що ти хочеш їсти сьогодні?", "Давай вчитися разом.", "Це мій друг.", "Вони вже тут, і це добре."),
        "ar" to arrayOf("ماذا تريد أن تأكل اليوم؟", "هيا ندرس معًا.", "هذا صديقي.", "لقد وصلوا بالفعل."),
        "hi" to arrayOf("आज तुम क्या खाना चाहते हो?", "चलो साथ में पढ़ते हैं।", "यह मेरा दोस्त है।", "वे पहले ही आ चुके हैं।"),
        "th" to arrayOf("วันนี้อยากกินอะไร", "ไปเรียนด้วยกันเถอะ", "นี่คือเพื่อนของฉัน", "พวกเขามาถึงแล้ว"),
        "nl" to arrayOf("Wat wil je vandaag eten?", "Ik weet het niet.", "Dat is mijn vriend.", "Zijn ze er al?"),
        "pl" to arrayOf("Co chcesz dziś zjeść?", "Nie wiem, czy to prawda.", "To jest mój przyjaciel.", "Jak się masz?"),
        "tr" to arrayOf("Bugün ne yemek istiyorsun?", "Bu çok güzel bir şey.", "Ben bilmiyorum.", "Sen de gel mi?"),
        "id" to arrayOf("Kamu mau makan apa hari ini?", "Aku tidak tahu.", "Ini temanku.", "Itu yang kamu maksud?"),
        "vi" to arrayOf("Hôm nay bạn muốn ăn gì?", "Tôi không biết.", "Đây là bạn của tôi.", "Anh có đến không?"),
    )

    @Test
    fun everyLanguageOfferedIsKnownByItsText() {
        assertEquals(SubtitleFiles.LANGUAGES.map { it.code }.toSet(), samples.keys)
        for ((code, lines) in samples) {
            assertEquals(code, LanguageDetector.detect(track(code, null, *lines)))
        }
    }

    @Test
    fun everyTagBelongsToOneLanguage() {
        val tags = SubtitleFiles.LANGUAGES.flatMap { it.tags }
        assertEquals(tags.size, tags.toSet().size)
        for (language in SubtitleFiles.LANGUAGES) {
            for (tag in language.tags) assertEquals(tag, language.code, SubtitleFiles.codeForTag(tag))
        }
    }

    @Test
    fun regionsAndScriptsAfterTheLanguageAreIgnored() {
        assertEquals("pt", SubtitleFiles.codeForTag("pt-BR"))
        assertEquals("es", SubtitleFiles.codeForTag("es-419"))
        assertEquals("fr", SubtitleFiles.codeForTag("fr-CA"))
        assertEquals("zh", SubtitleFiles.codeForTag("zh-Hant-TW"))
        assertEquals("en", SubtitleFiles.codeForTag("en_AU"))
        assertEquals("nl", SubtitleFiles.codeForTag("dut"))
        assertNull(SubtitleFiles.codeForTag("und-Latn"))
        // unknown languages are kept as they are, to show and to match
        assertEquals("tlh", SubtitleFiles.codeForTag("tlh"))
    }

    @Test
    fun picksTheTrackInTheChosenLanguage() {
        val japanese = track("Japanese", "jpn", *samples.getValue("ja"))
        val english = track("English", "eng", *samples.getValue("en"))
        val spanish = track("Spanish", "spa", *samples.getValue("es"))
        val french = track("French", "fr-CA", *samples.getValue("fr"))
        val tracks = listOf(japanese, english, spanish, french)
        val primary = SubtitleFiles.pickPrimary(tracks, "ja")
        assertSame(japanese, primary)
        assertSame(spanish, SubtitleFiles.pickSecondary(tracks, primary, "es"))
        assertSame(french, SubtitleFiles.pickSecondary(tracks, primary, "fr"))
        assertSame(english, SubtitleFiles.pickSecondary(tracks, primary, "en"))
        // no German track: nothing, rather than some other language
        assertNull(SubtitleFiles.pickSecondary(tracks, primary, "de"))
    }

    @Test
    fun untaggedTracksArePickedByTheirText() {
        val japanese = track("Track 1", "und", *samples.getValue("ja"))
        val thai = track("Track 2", "und", *samples.getValue("th"))
        val indonesian = track("Track 3", null, *samples.getValue("id"))
        val tracks = listOf(japanese, thai, indonesian)
        assertSame(thai, SubtitleFiles.pickSecondary(tracks, japanese, "th"))
        assertSame(indonesian, SubtitleFiles.pickSecondary(tracks, japanese, "id"))
    }

    @Test
    fun noPeekLanguageMeansNoPeekLine() {
        val japanese = track("Japanese", "jpn", *samples.getValue("ja"))
        val english = track("English", "eng", *samples.getValue("en"))
        assertNull(SubtitleFiles.pickSecondary(listOf(japanese, english), japanese, null))
    }

    @Test
    fun sidecarNamesCarryRegionalTags() {
        val brazilian = SubtitleFiles.parseSidecar("Episode 1.pt-BR.srt", srt(*samples.getValue("pt")))!!
        assertEquals("pt-BR", brazilian.language)
        assertEquals("pt", SubtitleFiles.effectiveLanguage(brazilian))
        // ".hi" is often "hearing impaired" rather than Hindi; the English text settles it
        val hearingImpaired = SubtitleFiles.parseSidecar("Episode 1.en.hi.srt", srt(*samples.getValue("en")))!!
        assertEquals("en", SubtitleFiles.effectiveLanguage(hearingImpaired))
        // release tags aren't languages
        assertNull(SubtitleFiles.parseSidecar("Episode 1.1080p.srt", srt(*samples.getValue("en")))!!.language)
    }

    @Test
    fun tagsWinWithinAScript() {
        // telling Russian from Ukrainian by letters is weaker than a tag
        assertEquals("ru", SubtitleFiles.effectiveLanguage(track("t", "rus", *samples.getValue("uk"))))
        // across scripts the text still wins
        assertEquals("ar", SubtitleFiles.effectiveLanguage(track("t", "eng", *samples.getValue("ar"))))
    }
}
