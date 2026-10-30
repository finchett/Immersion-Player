package app.mizune.subs

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * A subtitle file's bytes as text. Most files are UTF-8, but ones from subtitle sites are often in
 * their language's old Windows code page: Arabic in windows-1256, Russian in windows-1251, Japanese
 * in Shift_JIS. Read as UTF-8, those come out as question marks.
 *
 * In order: a byte-order mark; UTF-16 told by its zero bytes; UTF-8, if the bytes are valid UTF-8;
 * then the code pages of the languages the file is likely in (its name's tag, then the languages
 * the user reads), and failing those, every code page known here. Each is judged by whether the
 * text comes out made of that script's commonest letters, which wrong guesses rarely are.
 */
object SubtitleEncoding {

    /** A code page, by the names it goes by (Android lacks some of Java's), and what text in it looks like. */
    private class CodePage(
        val names: List<String>,
        /** The letters text in this code page mostly uses (lowercase), beyond ASCII. */
        val common: String,
        /** A Latin-script code page: most letters are ASCII, the rest accents. */
        val latin: Boolean = false,
    ) {
        val charset: Charset? = names.firstOrNull { runCatching { Charset.isSupported(it) }.getOrDefault(false) }
            ?.let(Charset::forName)
    }

    private val HIRAGANA = (0x3041..0x3096).joinToString("") { it.toChar().toString() } + "、。ー「」"

    private const val CJK_PUNCTUATION = "，。！？、“”…：「」"

    private val SHIFT_JIS = CodePage(listOf("windows-31j", "Shift_JIS"), HIRAGANA)
    private val GB18030 = CodePage(
        listOf("GB18030", "GBK"),
        "的一是不了人我在有他这中大来上们个国到说和地也子时道出要就下以生会自着去之过家学对可她里后小么心多天而能好都然没" + CJK_PUNCTUATION,
    )
    private val BIG5 = CodePage(
        listOf("Big5", "x-windows-950"),
        "的一是不了人我在有他這中大來上們個國到說和地也子時道出要就下以生會自著去之過家學對可她裡後小麼心多天而能好都然沒" + CJK_PUNCTUATION,
    )
    private val KOREAN = CodePage(
        listOf("x-windows-949", "EUC-KR"),
        "이다는의에고하가을를지어서요기로아도한리해나게사그수거자내있시정대일니마라습들저것때면말만네우주했오보무상전야너왜",
    )
    private val ARABIC = CodePage(listOf("windows-1256"), "اليمونرتبهأعكفدقسة")
    private val CYRILLIC = CodePage(listOf("windows-1251"), "оеаинтсрвлкі")
    private val HEBREW = CodePage(listOf("windows-1255"), "יוהלאמרבתשנ")
    private val GREEK = CodePage(listOf("windows-1253"), "αοειτνσηςρπκ")
    private val THAI = CodePage(listOf("x-windows-874", "windows-874", "TIS-620"), "านรอกเงมย่้ัีไ")
    private val CENTRAL_EUROPEAN = CodePage(listOf("windows-1250"), "ąęółśżźćńčřšžýůěťďňőű", latin = true)
    private val TURKISH = CodePage(listOf("windows-1254"), "ışğçöüâî", latin = true)
    private val BALTIC = CodePage(listOf("windows-1257"), "āčēģīķļņšūžąęėįųõäöü", latin = true)
    private val WESTERN = CodePage(listOf("windows-1252"), "éèàçêáíóúñãõâôüöäßìòùœëïû¿¡«»", latin = true)

    /** Every code page, in the order they're tried when nothing says which. */
    private val ALL = listOf(
        SHIFT_JIS, GB18030, BIG5, KOREAN, ARABIC, CYRILLIC, HEBREW, GREEK, THAI,
        WESTERN, CENTRAL_EUROPEAN, TURKISH, BALTIC,
    )

    /** Languages (ISO 639-1 and -2 codes) and the code pages their old files were written in. */
    private val BY_LANGUAGE: Map<String, List<CodePage>> = buildMap {
        fun put(codePages: List<CodePage>, vararg codes: String) = codes.forEach { put(it, codePages) }
        put(listOf(SHIFT_JIS), "ja", "jpn", "jp")
        put(listOf(GB18030, BIG5), "zh", "zho", "chi", "chs")
        put(listOf(BIG5, GB18030), "cht")
        put(listOf(KOREAN), "ko", "kor")
        put(listOf(ARABIC), "ar", "ara", "fa", "fas", "per", "ur", "urd")
        put(listOf(CYRILLIC), "ru", "rus", "uk", "ukr", "bg", "bul", "sr", "srp", "mk", "mkd", "mac", "be", "bel")
        put(listOf(HEBREW), "he", "heb", "iw", "yi", "yid")
        put(listOf(GREEK), "el", "ell", "gre")
        put(listOf(THAI), "th", "tha")
        put(listOf(TURKISH), "tr", "tur", "az", "aze")
        put(
            listOf(CENTRAL_EUROPEAN),
            "pl", "pol", "cs", "ces", "cze", "sk", "slk", "slo", "hu", "hun", "ro", "ron", "rum", "hr", "hrv", "sl", "slv",
        )
        put(listOf(BALTIC), "lt", "lit", "lv", "lav", "et", "est")
    }

    /** The code pages this Java can't decode, by name. A trimmed runtime without jdk.charsets lacks most. */
    fun missing(): List<String> = ALL.filter { it.charset == null }.map { it.names.first() }

    /** At least this share of the text's non-ASCII characters must be the code page's common letters. */
    private const val MIN_SCORE = 0.3

    /** What being in a likely language is worth: enough to settle a close call, not to beat clearly better text. */
    private const val LIKELY_BONUS = 0.15

    /**
     * [bytes] as text. [fileName]'s language tag ("Episode 1.ar.srt") and then [languages] (the ones
     * the user studies and peeks in) say which code pages to try first.
     */
    fun decode(bytes: ByteArray, fileName: String = "", languages: List<String> = emptyList()): String {
        bom(bytes)?.let { (charset, skip) -> return String(bytes, skip, bytes.size - skip, charset) }
        utf16(bytes)?.let { return String(bytes, it) }
        strict(bytes, Charsets.UTF_8)?.let { return it }

        val tag = fileName.substringBeforeLast('.').substringAfterLast('.', "")
        val likely = (listOf(tag) + languages)
            .flatMap { BY_LANGUAGE[it.lowercase().substringBefore('-').substringBefore('_')].orEmpty() }
            .toSet()
        // whichever fits best, with the likely ones given a head start
        ALL.mapNotNull { codePage -> attempt(bytes, codePage)?.let { (text, score) -> Triple(text, score, codePage in likely) } }
            .filter { it.second >= MIN_SCORE }
            .maxByOrNull { it.second + if (it.third) LIKELY_BONUS else 0.0 }
            ?.let { return it.first }
        // nothing fits: as before, UTF-8 with the odd character lost
        return String(bytes, Charsets.UTF_8)
    }

    /** The charset a byte-order mark names, and the mark's length. */
    private fun bom(bytes: ByteArray): Pair<Charset, Int>? {
        fun starts(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
        return when {
            starts(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            starts(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            starts(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> null
        }
    }

    /** UTF-16 without a mark: ASCII in it (the timings, at least) leaves every other byte zero. */
    private fun utf16(bytes: ByteArray): Charset? {
        val sample = bytes.size.coerceAtMost(4000) and 1.inv()
        if (sample < 32) return null
        var evenZeros = 0
        var oddZeros = 0
        for (i in 0 until sample step 2) {
            if (bytes[i] == 0.toByte()) evenZeros++
            if (bytes[i + 1] == 0.toByte()) oddZeros++
        }
        val pairs = sample / 2
        return when {
            oddZeros > pairs * 0.4 && evenZeros < pairs * 0.05 -> Charsets.UTF_16LE
            evenZeros > pairs * 0.4 && oddZeros < pairs * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    /** A run of letters (and the marks some scripts put on them). */
    private val WORD = Regex("[\\p{L}\\p{M}]+")

    /** [bytes] in [charset], or null if they aren't valid in it. */
    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** [bytes] in [codePage] and how well the text fits it (0 to 1), or null if they aren't valid in it. */
    private fun attempt(bytes: ByteArray, codePage: CodePage): Pair<String, Double>? {
        val charset = codePage.charset ?: return null
        val text = strict(bytes, charset) ?: return null
        val lower = text.lowercase()
        var other = 0
        var common = 0
        for (c in lower) {
            if (c.code < 0x80 || c.isWhitespace()) continue
            other++
            if (c in codePage.common) common++
        }
        if (other == 0) return text to 0.0
        // Latin accents sit inside ASCII words ("école"); other scripts make whole words of their own.
        // (Not a count of ASCII: an ASS file's headers are ASCII whatever its language.)
        val words = WORD.findAll(lower).map { it.value }.filter { word -> word.any { it.code >= 0x80 } }.toList()
        val mixed = words.count { word -> word.any { it.code < 0x80 } }
        val latinShaped = mixed * 2 >= words.size
        if (codePage.latin != latinShaped) return text to 0.0
        return text to common.toDouble() / other
    }
}
