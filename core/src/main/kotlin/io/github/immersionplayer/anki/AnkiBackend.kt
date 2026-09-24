package io.github.immersionplayer.anki

import java.io.File

/** Where cards go: desktop Anki through AnkiConnect, or AnkiDroid on the phone itself. */
interface AnkiBackend {
    fun noteTypes(): List<String>

    /** Field names of a note type, in order. */
    fun fieldNames(noteType: String): List<String>

    fun decks(): List<String>

    /** Ids of the notes in [deck] whose [field] is exactly [value]. */
    fun findNotes(deck: String, field: String, value: String): List<Long>

    /** Copies [file] into the collection's media and returns the name it was stored under. */
    fun storeMedia(file: File): String

    /** Adds a note and returns its id. */
    fun addNote(deck: String, noteType: String, fields: Map<String, String>, tags: List<String>): Long

    fun deleteNotes(ids: List<Long>)

    /**
     * Why the note would be refused, or null if it would be taken. The first field has to be
     * new to the deck, as Anki's own duplicate check has it.
     */
    fun cannotAdd(deck: String, noteType: String, fields: Map<String, String>): String? {
        if (deck !in decks()) return "deck was not found: $deck"
        val first = fieldNames(noteType).firstOrNull() ?: return "model was not found: $noteType"
        return if (findNotes(deck, first, fields[first].orEmpty()).isNotEmpty()) DUPLICATE else null
    }

    companion object {
        /** AnkiConnect's wording for a duplicate, which the other backends use too. */
        const val DUPLICATE = "cannot create note because it is a duplicate"

        /** A search term Anki takes literally: quoted, with its wildcards and quotes escaped. */
        fun quote(term: String) = "\"" + term.replace(Regex("""([\\"*_])"""), "\\\\$1") + "\""
    }
}

/** A card's media, made by whatever each platform has for cutting video. */
interface CardMedia {
    /** Extension of the images [image] writes, e.g. "avif" or "webp". */
    val imageExtension: String

    /** Writes the line's audio to [out] (Ogg Opus); false if it couldn't. */
    fun audio(word: MinedWord, out: File): Boolean

    /** Writes the line's picture to [out]; false if it couldn't. */
    fun image(word: MinedWord, out: File): Boolean
}

/** Where new cards go and how they're filled. */
class CardTarget(val format: CardFormat, val deck: String, val tags: List<String>)

/** Adds mined words to Anki: the checks first, then the media, then the note. */
class CardWriter(private val anki: AnkiBackend, private val mediaDir: File) {

    /** The note that was made, and which media couldn't be (e.g. "audio"). */
    class Added(val noteId: Long, val missing: List<String>)

    /** Anki already has this card; [noteId] is it, when it could be found. */
    class Duplicate(val noteId: Long?) : AnkiException("Already in Anki")

    fun add(word: MinedWord, target: CardTarget, media: CardMedia): Added {
        val format = target.format
        val textFields = word.fields(format)
        // before spending time on media: a closed Anki, a duplicate or a missing deck fail here
        anki.cannotAdd(target.deck, format.noteType, textFields)?.let { reason ->
            if (AnkiBackend.DUPLICATE in reason || "duplicate" in reason) {
                // remember which note it is, so it can be taken out again
                val first = anki.fieldNames(format.noteType).firstOrNull()
                throw Duplicate(first?.let { anki.findNotes(target.deck, it, textFields[it].orEmpty()).firstOrNull() })
            }
            throw AnkiException(friendly(reason, target))
        }

        val sources = format.fields.values.toSet()
        val base = word.mediaBaseName()
        mediaDir.mkdirs()
        val missing = mutableListOf<String>()
        fun make(extension: String, what: String, write: (File) -> Boolean): String? {
            val file = File(mediaDir, "$base.$extension")
            return try {
                if (write(file) && file.length() > 0) anki.storeMedia(file) else null.also { missing += what }
            } finally {
                file.delete()
            }
        }
        val audio = if (CardSource.SentenceAudio in sources) make("ogg", "audio") { media.audio(word, it) } else null
        val image = if (CardSource.Screenshot in sources) {
            make(media.imageExtension, "screenshot") { media.image(word, it) }
        } else null

        val fields = word.copy(audioFile = audio, imageFile = image).fields(format)
        return Added(anki.addNote(target.deck, format.noteType, fields, target.tags), missing)
    }

    fun remove(noteId: Long) = anki.deleteNotes(listOf(noteId))

    companion object {
        fun friendly(message: String, target: CardTarget) = when {
            "duplicate" in message -> "Already in Anki"
            "model was not found" in message -> "Note type \"${target.format.noteType}\" isn't in Anki"
            "deck was not found" in message -> "Deck \"${target.deck}\" isn't in Anki"
            else -> message
        }
    }
}
