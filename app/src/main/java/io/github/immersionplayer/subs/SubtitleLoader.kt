package io.github.immersionplayer.subs

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Finds the subtitle tracks for a video: sidecar files first, then tracks embedded in the file. */
class SubtitleLoader(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "subtitles").apply { mkdirs() }

    fun load(video: DocumentFile, siblings: List<DocumentFile>): List<SubtitleTrack> {
        val sidecars = findSidecars(video, siblings).mapNotNull { readSidecar(it) }
        if (sidecars.isNotEmpty()) return sidecars
        if (!isMatroska(video)) return emptyList()
        return loadEmbedded(video.uri, video.length(), video.lastModified())
    }

    private fun findSidecars(video: DocumentFile, siblings: List<DocumentFile>): List<DocumentFile> {
        val base = video.name?.substringBeforeLast('.') ?: return emptyList()
        return siblings.filter { file ->
            val name = file.name ?: return@filter false
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in SUBTITLE_EXTENSIONS && name.startsWith("$base.")
        }
    }

    private fun readSidecar(file: DocumentFile): SubtitleTrack? {
        val name = file.name ?: return null
        val content = context.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        val cues = when (name.substringAfterLast('.').lowercase()) {
            "ass", "ssa" -> AssParser.parse(content)
            else -> SrtParser.parse(content)
        }
        if (cues.isEmpty()) return null
        // "Episode 1.ja.srt" -> "ja"
        val language = name.substringBeforeLast('.').substringAfterLast('.', "").takeIf { it.length in 2..3 }
        return SubtitleTrack(name, language, cues)
    }

    private fun isMatroska(video: DocumentFile): Boolean {
        val ext = video.name?.substringAfterLast('.', "")?.lowercase()
        return ext == "mkv" || ext == "mka" || ext == "webm"
    }

    private fun loadEmbedded(uri: Uri, length: Long, modified: Long): List<SubtitleTrack> {
        val cacheFile = File(cacheDir, cacheKey("$uri|$length|$modified") + ".json")
        if (cacheFile.exists()) {
            runCatching { return fromJson(cacheFile.readText()) }
        }
        val tracks = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                MatroskaSubtitleExtractor(channel).extract()
            }
        }.orEmpty()
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

    companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")

        private val JAPANESE = setOf("ja", "jpn", "jp", "ja-jp")
        private val ENGLISH = setOf("en", "eng", "en-us", "en-gb")

        fun isJapanese(track: SubtitleTrack) = track.language?.lowercase() in JAPANESE
        fun isEnglish(track: SubtitleTrack) = track.language?.lowercase() in ENGLISH

        /** Japanese track to study with; falls back to the first track. */
        fun pickPrimary(tracks: List<SubtitleTrack>): SubtitleTrack? =
            tracks.firstOrNull(::isJapanese) ?: tracks.firstOrNull()

        /** English "full" track to show on demand, skipping signs-only tracks. */
        fun pickSecondary(tracks: List<SubtitleTrack>, primary: SubtitleTrack?): SubtitleTrack? {
            val english = tracks.filter { it !== primary && isEnglish(it) }
            return english.firstOrNull { !it.name.contains("sign", ignoreCase = true) }
                ?: english.maxByOrNull { it.cues.size }
        }
    }
}
