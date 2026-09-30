package io.github.immersionplayer.media

import io.github.immersionplayer.subs.SubtitleFiles

/** An audio track as mpv lists it; [id] is what mpv's `aid` takes. */
data class AudioTrack(val id: Int, val language: String?, val title: String?, val codec: String?) {

    /** "Japanese · Main · aac": the language by name where known, then whatever else the file says. */
    val label: String
        get() {
            val name = language?.let { tag -> SubtitleFiles.codeForTag(tag)?.let(SubtitleFiles::language)?.name ?: tag }
            return listOfNotNull(name, title, codec).distinct().joinToString(" · ").ifEmpty { "Track $id" }
        }

    companion object {
        /** The audio tracks in mpv's `track-list`, read through [property] (mpv's property as a string). */
        fun fromMpv(property: (String) -> String?): List<AudioTrack> {
            val count = property("track-list/count")?.toIntOrNull() ?: return emptyList()
            return (0 until count).mapNotNull { i ->
                if (property("track-list/$i/type") != "audio") return@mapNotNull null
                val id = property("track-list/$i/id")?.toIntOrNull() ?: return@mapNotNull null
                AudioTrack(
                    id = id,
                    language = property("track-list/$i/lang")?.takeIf { it.isNotBlank() },
                    title = property("track-list/$i/title")?.takeIf { it.isNotBlank() },
                    codec = property("track-list/$i/codec")?.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}
