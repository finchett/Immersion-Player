package io.github.immersionplayer.anki

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * AnkiDroid's content provider (its FlashCardsContract), used directly rather than through
 * AnkiDroid's API library, which is only published on JitPack. Needs AnkiDroid installed and
 * [PERMISSION] granted; calls block, so keep them off the main thread.
 */
class AnkiDroid(private val context: Context) : AnkiBackend {
    private val resolver = context.contentResolver

    override fun noteTypes(): List<String> = models().map { it.name }

    override fun fieldNames(noteType: String): List<String> =
        models().firstOrNull { it.name == noteType }?.fields.orEmpty()

    override fun decks(): List<String> = deckIds().keys.toList()

    override fun findNotes(deck: String, field: String, value: String): List<Long> {
        // the provider's v1 notes table takes an Anki search as its selection
        val search = AnkiBackend.quote("deck:$deck") + " " + AnkiBackend.quote("$field:$value")
        // a search that finds nothing comes back as no cursor at all, not an empty one
        return query(NOTES, arrayOf("_id"), search, whenNone = emptyList()) { it.getLong(0) }
    }

    override fun storeMedia(file: File): String {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.grantUriPermission(PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val values = ContentValues().apply {
                put("file_uri", uri.toString())
                put("preferred_name", file.nameWithoutExtension)
            }
            val stored = call { resolver.insert(MEDIA, values) } ?: throw AnkiException("AnkiDroid didn't take ${file.name}")
            // content://com.ichi2.anki.flashcards/media/<name as stored>
            return File(stored.path ?: file.name).name
        } finally {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun addNote(deck: String, noteType: String, fields: Map<String, String>, tags: List<String>): Long {
        val model = models().firstOrNull { it.name == noteType } ?: throw AnkiException("model was not found: $noteType")
        val deckId = deckIds()[deck] ?: throw AnkiException("deck was not found: $deck")
        val values = ContentValues().apply {
            put("mid", model.id)
            put("flds", model.fields.joinToString(FIELD_SEPARATOR) { fields[it].orEmpty() })
            put("tags", tags.joinToString(" "))
        }
        val note = call { resolver.insert(NOTES, values) } ?: throw AnkiException("AnkiDroid didn't add the card")
        // a new note's cards go to the note type's deck; move them to the chosen one, as
        // AnkiDroid's own API does
        val cards = Uri.withAppendedPath(note, "cards")
        val ords = query(cards, arrayOf("ord"), null, what = "the new card") { it.getString(0) }
        for (ord in ords) {
            call { resolver.update(Uri.withAppendedPath(cards, ord), ContentValues().apply { put("deck_id", deckId) }, null, null) }
        }
        return note.lastPathSegment?.toLongOrNull() ?: throw AnkiException("AnkiDroid didn't say which note it added")
    }

    override fun deleteNotes(ids: List<Long>) {
        for (id in ids) {
            val deleted = call { resolver.delete(Uri.withAppendedPath(NOTES, id.toString()), null, null) }
            if (deleted == 0) throw AnkiException("AnkiDroid didn't remove the card")
        }
    }

    private class Model(val id: Long, val name: String, val fields: List<String>)

    private fun models(): List<Model> = query(MODELS, arrayOf("_id", "name", "field_names"), null, what = "note types") {
        Model(it.getLong(0), it.getString(1), it.getString(2).split(FIELD_SEPARATOR))
    }

    private fun deckIds(): Map<String, Long> =
        query(DECKS, arrayOf("deck_id", "deck_name"), null, what = "decks") { it.getString(1) to it.getLong(0) }.toMap()

    /** Rows of [uri]; no cursor is [whenNone] if given, otherwise an error naming [what] was asked for. */
    private fun <T> query(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        whenNone: List<T>? = null,
        what: String = "the search",
        row: (android.database.Cursor) -> T,
    ): List<T> =
        call {
            resolver.query(uri, columns, selection, null, null)?.use { cursor ->
                buildList { while (cursor.moveToNext()) add(row(cursor)) }
            }
        } ?: whenNone ?: throw AnkiException(unavailable(context) ?: "AnkiDroid didn't answer about $what")

    /** Runs a provider call, turning AnkiDroid's refusals into messages. */
    private fun <T> call(block: () -> T): T {
        unavailable(context)?.let { throw AnkiException(it) }
        return try {
            block()
        } catch (e: SecurityException) {
            throw AnkiException("Immersion Player isn't allowed to use AnkiDroid yet", e)
        } catch (e: IllegalArgumentException) {
            throw AnkiException(e.message ?: "AnkiDroid refused the card", e)
        }
    }

    companion object {
        const val PACKAGE = "com.ichi2.anki"
        const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val FIELD_SEPARATOR = "\u001f"
        private val AUTHORITY = Uri.parse("content://com.ichi2.anki.flashcards")
        private val NOTES = Uri.withAppendedPath(AUTHORITY, "notes")
        private val MODELS = Uri.withAppendedPath(AUTHORITY, "models")
        private val DECKS = Uri.withAppendedPath(AUTHORITY, "decks")
        private val MEDIA = Uri.withAppendedPath(AUTHORITY, "media")

        fun installed(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

        fun permitted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

        /** Why AnkiDroid can't be used yet, or null when it can. */
        fun unavailable(context: Context): String? = when {
            !installed(context) -> "AnkiDroid isn't installed"
            !permitted(context) -> "Immersion Player isn't allowed to use AnkiDroid yet"
            else -> null
        }
    }
}
