package io.github.immersionplayer.desktop

import androidx.compose.runtime.mutableStateMapOf
import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.anki.AnkiException
import io.github.immersionplayer.anki.CardSource
import io.github.immersionplayer.anki.MinedWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Adds cards to Anki through AnkiConnect, one at a time, with the media cut from the video. */
class AnkiCards(
    private val settings: Settings,
    cacheDir: File,
    private val logError: (String, Throwable) -> Unit,
) {
    /** A card to make: the word in its line, and where in the video to take media from. */
    class Request(val word: MinedWord, val video: File, val audioTrack: String?, val screenshotAt: Double)

    sealed class Status(val message: String) {
        class Working(word: String) : Status("Adding $word to Anki…")
        class Added(word: String, missing: String?) : Status("Added $word to Anki" + (missing?.let { " (no $it)" } ?: ""))
        class Removing(word: String) : Status("Removing $word from Anki…")
        class Removed(word: String) : Status("Removed $word from Anki")
        class Failed(message: String) : Status(message)
    }

    private val _status = MutableStateFlow<Status?>(null)
    /** What the last request is doing or did, shown for a few seconds after it finishes. */
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** Notes of words (expression and sentence) added, or found already in Anki, since the app started. */
    private val notes = mutableStateMapOf<Pair<String, String>, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private val mediaDir = File(cacheDir, "anki")
    private var clearJob: Job? = null

    fun isAdded(word: MinedWord) = word.key in notes

    fun add(request: Request) = run(request.word.entry.expression, Status.Working(request.word.entry.expression)) {
        add(request.word, request)
    }

    /** Deletes the note made of [word] from Anki. */
    fun remove(word: MinedWord) = run(word.entry.expression, Status.Removing(word.entry.expression)) {
        val id = notes[word.key] ?: return@run Status.Failed("That card isn't known to be in Anki")
        AnkiConnect(settings.ankiUrl).deleteNotes(listOf(id))
        notes.remove(word.key)
        Status.Removed(word.entry.expression)
    }

    /** Runs [work] after any earlier request, showing [working] meanwhile and then how it went. */
    private fun run(expression: String, working: Status, work: () -> Status) {
        scope.launch {
            queue.withLock {
                clearJob?.cancel()
                _status.value = working
                _status.value = try {
                    work()
                } catch (e: AnkiException) {
                    Status.Failed(friendly(e.message ?: "Anki refused the card"))
                } catch (e: Exception) {
                    logError("anki card $expression", e)
                    Status.Failed("Couldn't change the card: ${e.message}")
                }
                clearJob = scope.launch {
                    delay(4_000)
                    _status.value = null
                }
            }
        }
    }

    private val MinedWord.key get() = entry.expression to sentence

    private fun add(word: MinedWord, request: Request): Status {
        val format = settings.ankiFormat ?: return Status.Failed("Choose a note type under Settings › Anki")
        val deck = settings.ankiDeck ?: return Status.Failed("Choose a deck under Settings › Anki")
        val anki = AnkiConnect(settings.ankiUrl)
        // before spending time on media: a closed Anki, a duplicate or a missing deck fail here
        val textFields = word.fields(format)
        anki.cannotAdd(deck, format.noteType, textFields)?.let { reason ->
            // already there: remember which note it is, so the button can take it out again
            if ("duplicate" in reason) {
                val first = anki.modelFieldNames(format.noteType).firstOrNull()
                val id = first?.let { anki.findNotes(deck, it, textFields[it].orEmpty()).firstOrNull() }
                if (id != null) notes[word.key] = id
            }
            throw AnkiException(reason)
        }

        val sources = format.fields.values.toSet()
        val base = word.mediaBaseName()
        mediaDir.mkdirs()
        val missing = mutableListOf<String>()

        var audio: String? = null
        if (CardSource.SentenceAudio in sources) {
            val file = File(mediaDir, "$base.ogg")
            val padding = settings.ankiAudioPadding
            if (CardMedia.audioClip(request.video, word.start - padding, word.end + padding, request.audioTrack, file)) {
                audio = anki.storeMediaFile(file.name, file.readBytes())
            } else {
                missing += "audio"
            }
            file.delete()
        }
        var image: String? = null
        if (CardSource.Screenshot in sources) {
            val file = File(mediaDir, "$base.avif")
            val height = settings.ankiImageHeight
            val made = if (settings.ankiImageAnimated) {
                CardMedia.animation(request.video, word.start, word.end, height, file)
            } else {
                CardMedia.screenshot(request.video, request.screenshotAt, height, file)
            }
            if (made) {
                image = anki.storeMediaFile(file.name, file.readBytes())
            } else {
                missing += "screenshot"
            }
            file.delete()
        }

        val fields = word.copy(audioFile = audio, imageFile = image).fields(format)
        val tags = settings.ankiTags.split(' ', '　').filter { it.isNotBlank() }
        notes[word.key] = anki.addNote(deck, format.noteType, fields, tags)
        return Status.Added(word.entry.expression, missing.joinToString(" or ").ifEmpty { null })
    }

    private fun friendly(message: String) = when {
        "duplicate" in message -> "Already in Anki"
        "model was not found" in message -> "Note type \"${settings.ankiNoteType}\" isn't in Anki"
        "deck was not found" in message -> "Deck \"${settings.ankiDeck}\" isn't in Anki"
        else -> message
    }
}
