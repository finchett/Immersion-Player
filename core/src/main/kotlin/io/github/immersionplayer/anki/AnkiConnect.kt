package io.github.immersionplayer.anki

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

class AnkiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The AnkiConnect add-on's HTTP API (version 6). The same API is served by desktop Anki with
 * AnkiConnect installed and by AnkiConnect Android, so this works against either.
 */
class AnkiConnect(private val url: String = DEFAULT_URL) {

    fun version(): Int = call("version") as Int

    fun deckNames(): List<String> = strings(call("deckNames"))

    fun modelNames(): List<String> = strings(call("modelNames"))

    /** Field names of a note type, in order. */
    fun modelFieldNames(model: String): List<String> =
        strings(call("modelFieldNames", JSONObject().put("modelName", model)))

    /** Saves a file into Anki's media folder and returns the name Anki stored it under. */
    fun storeMediaFile(filename: String, data: ByteArray): String =
        call(
            "storeMediaFile",
            JSONObject().put("filename", filename).put("data", Base64.getEncoder().encodeToString(data)),
        ) as? String ?: filename

    /** Adds a note and returns its id. Anki refuses duplicates of the first field within the deck. */
    fun addNote(deck: String, model: String, fields: Map<String, String>, tags: List<String>): Long =
        (call("addNote", JSONObject().put("note", note(deck, model, fields).put("tags", JSONArray(tags)))) as Number).toLong()

    /**
     * Why Anki would refuse this note (a duplicate, a missing deck), or null if it would take it,
     * or if this AnkiConnect is too old to say.
     */
    fun cannotAdd(deck: String, model: String, fields: Map<String, String>): String? {
        val result = try {
            call("canAddNotesWithErrorDetail", JSONObject().put("notes", JSONArray().put(note(deck, model, fields))))
        } catch (e: AnkiException) {
            if ("unsupported action" in e.message.orEmpty()) return null else throw e
        }
        val detail = (result as? JSONArray)?.optJSONObject(0) ?: return null
        return if (detail.optBoolean("canAdd", true)) null else detail.optString("error", "Anki won't add this card")
    }

    /** Ids of the notes in [deck] whose [field] is exactly [value]. */
    fun findNotes(deck: String, field: String, value: String): List<Long> {
        val result = call("findNotes", JSONObject().put("query", "${quote("deck:$deck")} ${quote("$field:$value")}"))
        val array = result as? JSONArray ?: return emptyList()
        return (0 until array.length()).map { array.getLong(it) }
    }

    fun deleteNotes(ids: List<Long>) {
        call("deleteNotes", JSONObject().put("notes", JSONArray(ids)))
    }

    /** A search term Anki takes literally: quoted, with its wildcards and quotes escaped. */
    private fun quote(term: String) = "\"" + term.replace(Regex("""([\\"*_])"""), "\\\\$1") + "\""

    private fun note(deck: String, model: String, fields: Map<String, String>) = JSONObject()
        .put("deckName", deck)
        .put("modelName", model)
        .put("fields", JSONObject(fields))
        .put("options", JSONObject().put("allowDuplicate", false))

    private fun call(action: String, params: JSONObject? = null): Any? {
        val body = JSONObject().put("action", action).put("version", 6)
        if (params != null) body.put("params", params)
        val response = try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            // adding a note can run other add-ons' hooks (furigana, pitch), which take a moment
            connection.readTimeout = 30_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: ConnectException) {
            throw AnkiException("Anki isn't running, or AnkiConnect isn't installed", e)
        } catch (e: IOException) {
            throw AnkiException("Couldn't reach Anki: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw AnkiException("Not a valid address: $url", e)
        }
        val json = runCatching { JSONObject(response) }.getOrElse {
            throw AnkiException("Unexpected reply from $url")
        }
        if (!json.isNull("error")) throw AnkiException(json.get("error").toString())
        return json.opt("result")
    }

    private fun strings(value: Any?): List<String> {
        val array = value as? JSONArray ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8765"
    }
}
