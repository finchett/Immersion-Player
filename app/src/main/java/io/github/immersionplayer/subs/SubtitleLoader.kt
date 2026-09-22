package io.github.immersionplayer.subs

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream

/** Finds the subtitle tracks for a video: sidecar files first, then tracks embedded in the file. */
class SubtitleLoader(private val context: Context) {

    private val cache = EmbeddedSubtitleCache(File(context.cacheDir, "subtitles"))

    fun load(video: DocumentFile, siblings: List<DocumentFile>): List<SubtitleTrack> {
        val videoName = video.name ?: return emptyList()
        val sidecars = siblings
            .filter { file -> file.name?.let { SubtitleFiles.isSidecarFor(videoName, it) } == true }
            .mapNotNull { readSidecar(it) }
        if (sidecars.isNotEmpty()) return sidecars
        if (!SubtitleFiles.isMatroska(videoName)) return emptyList()
        return cache.load("${video.uri}|${video.length()}|${video.lastModified()}") {
            context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { MatroskaSubtitleExtractor(it).extract() }
            }.orEmpty()
        }
    }

    private fun readSidecar(file: DocumentFile): SubtitleTrack? {
        val name = file.name ?: return null
        val content = context.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        return SubtitleFiles.parseSidecar(name, content)
    }
}
