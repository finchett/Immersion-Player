package app.mizune.subs

import android.content.Context
import app.mizune.library.LibraryFile
import java.io.File
import java.io.FileInputStream

/** Finds the subtitle tracks for a video: sidecar files first, then tracks embedded in the file in other languages. */
class SubtitleLoader(private val context: Context) {

    private val cache = EmbeddedSubtitleCache(File(context.cacheDir, "subtitles"))

    /** [languages] (the target and peek language) help tell what a sidecar file's bytes are in. */
    fun load(video: LibraryFile, siblings: List<LibraryFile>, languages: List<String>): List<SubtitleTrack> {
        val videoName = video.name.ifEmpty { return emptyList() }
        val sidecars = sidecarsFor(videoName, siblings).mapNotNull { readSidecar(it, languages) }
        if (!SubtitleFiles.hasReadableTracks(videoName)) return sidecars
        val embedded = runCatching {
            cache.load(identity(video)) {
                context.contentResolver.openFileDescriptor(video.uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).channel.use { SubtitleFiles.extractEmbedded(videoName, it) }
                }.orEmpty()
            }
        }.getOrElse {
            // the files beside it still play
            if (sidecars.isEmpty()) throw it
            emptyList()
        }
        return SubtitleFiles.withEmbedded(sidecars, embedded)
    }

    /**
     * [video]'s image (PGS) subtitles in [languages] (the target and peek language), when no text
     * track in [text] covers them: ready at once with their timing, their text read by OCR as
     * they're played (see [PictureLines]) and kept for next time. Scans the file, so call it off
     * the main thread; [PictureLines.close] them when the player goes.
     */
    fun openPictureTracks(video: LibraryFile, text: List<SubtitleTrack>, languages: List<String>): List<SubtitleTrack> {
        val videoName = video.name.ifEmpty { return emptyList() }
        if (!SubtitleFiles.isMatroska(videoName)) return emptyList()
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

    private fun sidecarsFor(videoName: String, siblings: List<LibraryFile>) =
        siblings.filter { file -> SubtitleFiles.isSidecarFor(videoName, file.name) }

    private fun identity(video: LibraryFile) = "${video.uri}|${video.length}|${video.lastModified}"

    private fun readSidecar(file: LibraryFile, languages: List<String>): SubtitleTrack? {
        val name = file.name.ifEmpty { return null }
        val content = context.contentResolver.openInputStream(file.uri)?.use {
            SubtitleEncoding.decode(it.readBytes(), name, languages)
        } ?: return null
        return SubtitleFiles.parseSidecar(name, content)
    }
}
