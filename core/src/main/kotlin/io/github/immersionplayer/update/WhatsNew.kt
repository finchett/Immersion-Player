package io.github.immersionplayer.update

import org.json.JSONObject
import java.io.File

/**
 * The notes of the update on its way in, kept until the version they describe is running, so
 * its first launch can show what's new. An update read about before it went in isn't shown again.
 */
class WhatsNew(private val file: File) {
    /** Called once an update is downloaded. */
    fun expect(version: String, notes: String) {
        if (notes.isBlank()) return
        runCatching { file.writeText(JSONObject().put("version", version).put("notes", notes).toString()) }
    }

    /** The notes to show now: the update just installed, if they haven't been read yet. */
    fun pending(currentVersion: String): String? = runCatching {
        val saved = JSONObject(file.readText())
        saved.getString("notes").takeIf { saved.getString("version") == currentVersion }
    }.getOrNull()

    /** Read, before or after the update. */
    fun seen() {
        file.delete()
    }
}
