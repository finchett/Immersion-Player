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
        val sidecars = sidecarsFor(videoName, siblings).mapNotNull { readSidecar(it) }
        if (sidecars.isNotEmpty()) return sidecars
        if (!SubtitleFiles.isMatroska(videoName)) return emptyList()
        return cache.load(identity(video)) {
            context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { MatroskaSubtitleExtractor(it).extract() }
            }.orEmpty()
        }
    }

    /**
     * [video]'s image (PGS) subtitles in [languages] (the target and peek language), when no text
     * track in [text] covers them: ready at once with their timing, their text read by OCR as
     * they're played (see [PictureLines]) and kept for next time. Scans the file, so call it off
     * the main thread; [PictureLines.close] them when the player goes.
     */
    fun openPictureTracks(
        video: DocumentFile,
        siblings: List<DocumentFile>,
        text: List<SubtitleTrack>,
        languages: List<String>,
    ): List<SubtitleTrack> {
        val videoName = video.name ?: return emptyList()
        if (!SubtitleFiles.isMatroska(videoName) || sidecarsFor(videoName, siblings).isNotEmpty()) return emptyList()
        val needed = languages.filter { code -> ImageSubtitles.model(code) != null && text.none { SubtitleFiles.isLanguage(it, code) } }
        if (needed.isEmpty()) return emptyList()
        val images = context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { MatroskaSubtitleExtractor(it).extractAll().second }
        }.orEmpty()
        return ImageSubtitles.tracksToRead(text, images, needed).map { track ->
            val model = ImageSubtitles.model(track)!!
            ImageSubtitles.open(
                track,
                newRecognizer = { TesseractRecognizer(context, model) },
                store = cache.lineStore("${identity(video)}|ocr|${track.name}|${track.lineCount}"),
            )
        }
    }

    private fun sidecarsFor(videoName: String, siblings: List<DocumentFile>) =
        siblings.filter { file -> file.name?.let { SubtitleFiles.isSidecarFor(videoName, it) } == true }

    private fun identity(video: DocumentFile) = "${video.uri}|${video.length()}|${video.lastModified()}"

    private fun readSidecar(file: DocumentFile): SubtitleTrack? {
        val name = file.name ?: return null
        val content = context.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        return SubtitleFiles.parseSidecar(name, content)
    }
}
