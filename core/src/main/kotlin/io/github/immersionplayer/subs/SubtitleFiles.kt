package io.github.immersionplayer.subs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Platform-free parts of subtitle loading: sidecar parsing, track choice, the embedded-track cache. */
object SubtitleFiles {
    val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
    private val MATROSKA_EXTENSIONS = setOf("mkv", "mka", "webm")

    /** A language users can pick, with the tags subtitle files and Matroska tracks use for it. */
    data class Language(val code: String, val name: String, val tags: Set<String>)

    val LANGUAGES = listOf(
        Language("ja", "Japanese", setOf("ja", "jpn", "jp", "ja-jp")),
        Language("en", "English", setOf("en", "eng", "en-us", "en-gb")),
        Language("zh", "Chinese", setOf("zh", "zho", "chi", "zh-cn", "zh-tw", "zh-hans", "zh-hant", "chs", "cht")),
        Language("ko", "Korean", setOf("ko", "kor", "ko-kr")),
        Language("es", "Spanish", setOf("es", "spa", "es-es", "es-419", "es-mx")),
        Language("fr", "French", setOf("fr", "fre", "fra", "fr-fr")),
        Language("de", "German", setOf("de", "ger", "deu", "de-de")),
        Language("pt", "Portuguese", setOf("pt", "por", "pt-br", "pt-pt")),
        Language("it", "Italian", setOf("it", "ita")),
        Language("ru", "Russian", setOf("ru", "rus")),
    )

    fun language(code: String): Language? = LANGUAGES.firstOrNull { it.code == code }

    /** The language code a tag like "jpn" or "ja-JP" stands for, or the tag itself if unknown. */
    fun codeForTag(tag: String?): String? {
        val lower = tag?.lowercase() ?: return null
        return LANGUAGES.firstOrNull { lower in it.tags }?.code ?: lower
    }

    /** Whether [track] is in [code], by its text when its tag is clearly wrong (see [LanguageDetector]). */
    fun isLanguage(track: SubtitleTrack, code: String): Boolean =
        effectiveLanguage(track) == code

    fun effectiveLanguage(track: SubtitleTrack): String? =
        LanguageDetector.effectiveLanguage(track, codeForTag(track.language))

    fun isJapanese(track: SubtitleTrack) = isLanguage(track, "ja")
    fun isEnglish(track: SubtitleTrack) = isLanguage(track, "en")

    fun isMatroska(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in MATROSKA_EXTENSIONS

    /** True when [fileName] is a subtitle file named after the video [videoName]. */
    fun isSidecarFor(videoName: String, fileName: String): Boolean {
        val base = videoName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUBTITLE_EXTENSIONS && fileName.startsWith("$base.")
    }

    fun parseSidecar(fileName: String, content: String): SubtitleTrack? {
        val cues = when (fileName.substringAfterLast('.').lowercase()) {
            "ass", "ssa" -> AssParser.parse(content)
            else -> SrtParser.parse(content)
        }
        if (cues.isEmpty()) return null
        // "Episode 1.ja.srt" -> "ja"
        val language = fileName.substringBeforeLast('.').substringAfterLast('.', "").takeIf { it.length in 2..3 }
        return SubtitleTrack(fileName, language, cues)
    }

    /** Track in the language being studied; falls back to the first track. */
    fun pickPrimary(tracks: List<SubtitleTrack>, target: String = "ja"): SubtitleTrack? =
        // the fullest matching track: dialogue over signs-and-songs
        tracks.filter { isLanguage(it, target) }.maxByOrNull { it.cues.size } ?: tracks.firstOrNull()

    /** Full track in the [peek] language (null for none), skipping signs-only tracks. */
    fun pickSecondary(tracks: List<SubtitleTrack>, primary: SubtitleTrack?, peek: String? = "en"): SubtitleTrack? {
        if (peek == null) return null
        val candidates = tracks.filter { it !== primary && isLanguage(it, peek) }
        return candidates.firstOrNull { !it.name.contains("sign", ignoreCase = true) }
            ?: candidates.maxByOrNull { it.cues.size }
    }
}

/** Embedded Matroska tracks, extracted once per file version and kept as JSON in [dir]. */
class EmbeddedSubtitleCache(private val dir: File) {
    init {
        dir.mkdirs()
    }

    /** [identity] must change when the file does, e.g. "uri|length|modified". */
    fun load(identity: String, extract: () -> List<SubtitleTrack>): List<SubtitleTrack> {
        val cacheFile = File(dir, cacheKey(identity) + ".json")
        if (cacheFile.exists()) {
            runCatching { return fromJson(cacheFile.readText()) }
        }
        val tracks = extract()
        cacheFile.writeText(toJson(tracks))
        return tracks
    }

    private fun cacheKey(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun toJson(tracks: List<SubtitleTrack>): String {
        val array = JSONArray()
        for (track in tracks) {
            val cues = JSONArray()
            for (cue in track.cues) {
                cues.put(JSONArray().put(cue.start).put(cue.end).put(cue.text))
            }
            array.put(
                JSONObject()
                    .put("name", track.name)
                    .put("language", track.language ?: JSONObject.NULL)
                    .put("cues", cues)
            )
        }
        return array.toString()
    }

    private fun fromJson(json: String): List<SubtitleTrack> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val cues = obj.getJSONArray("cues")
            SubtitleTrack(
                name = obj.getString("name"),
                language = if (obj.isNull("language")) null else obj.getString("language"),
                cues = (0 until cues.length()).map { j ->
                    val c = cues.getJSONArray(j)
                    Cue(c.getDouble(0), c.getDouble(1), c.getString(2))
                },
            )
        }
    }
}
