package io.github.immersionplayer.dictionary

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Imports Yomitan/Yomichan dictionary zips (term banks and term meta banks). */
class YomitanImporter(
    private val context: Context,
    private val database: DictionaryDatabase,
) {
    data class Progress(val file: String, val terms: Int, val meta: Int)

    class ImportException(message: String) : Exception(message)

    fun import(uri: Uri, fileName: String, onProgress: (Progress) -> Unit): DictionaryInfo =
        import(fileName, onProgress) {
            context.contentResolver.openInputStream(uri) ?: throw ImportException("Could not open $fileName")
        }

    fun import(fileName: String, onProgress: (Progress) -> Unit, open: () -> InputStream): DictionaryInfo {
        val db = database.writableDatabase
        val dictId = db.insertOrThrow(
            "dictionaries",
            null,
            ContentValues().apply {
                put("title", fileName.removeSuffix(".zip"))
                put("priority", nextPriority())
            },
        )

        var title: String? = null
        var revision = ""
        var format = 3
        var termCount = 0
        var metaCount = 0
        var sawBank = false

        try {
            ZipInputStream(open().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    when {
                        name == "index.json" -> {
                            val index = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            title = index.optString("title").ifEmpty { null }
                            revision = index.optString("revision")
                            format = index.optInt("format", index.optInt("version", 3))
                        }
                        name.startsWith("term_bank_") && name.endsWith(".json") -> {
                            sawBank = true
                            val bank = JSONArray(zip.readBytes().toString(Charsets.UTF_8))
                            termCount += insertTerms(dictId, bank, format)
                            onProgress(Progress(name, termCount, metaCount))
                        }
                        name.startsWith("term_meta_bank_") && name.endsWith(".json") -> {
                            sawBank = true
                            val bank = JSONArray(zip.readBytes().toString(Charsets.UTF_8))
                            metaCount += insertMeta(dictId, bank)
                            onProgress(Progress(name, termCount, metaCount))
                        }
                    }
                }
            }
            if (title == null && !sawBank) throw ImportException("$fileName is not a Yomitan dictionary")
            if (!sawBank) throw ImportException("$fileName has no term or frequency data (kanji-only dictionaries are not supported yet)")
        } catch (e: Exception) {
            database.delete(dictId)
            throw e
        }

        val finalTitle = title ?: fileName.removeSuffix(".zip")
        // re-importing a dictionary replaces the old copy
        db.rawQuery("SELECT id FROM dictionaries WHERE title = ? AND id != ?", arrayOf(finalTitle, dictId.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            .forEach(database::delete)

        db.update(
            "dictionaries",
            ContentValues().apply {
                put("title", finalTitle)
                put("revision", revision)
                put("term_count", termCount)
                put("meta_count", metaCount)
                put("complete", 1)
            },
            "id = ?",
            arrayOf(dictId.toString()),
        )
        return database.dictionaries().first { it.id == dictId }
    }

    private fun nextPriority(): Int =
        database.readableDatabase.rawQuery("SELECT COALESCE(MAX(priority), -1) + 1 FROM dictionaries", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun insertTerms(dictId: Long, bank: JSONArray, format: Int): Int {
        val db = database.writableDatabase
        val statement = db.compileStatement(
            "INSERT INTO terms (dict_id, expression, reading, definition_tags, rules, score, glossary, sequence, term_tags) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        db.beginTransaction()
        try {
            for (i in 0 until bank.length()) {
                val row = bank.optJSONArray(i) ?: continue
                val expression = row.optString(0)
                if (expression.isEmpty()) continue
                val reading = row.optString(1).ifEmpty { expression }

                val glossary: JSONArray
                val sequence: Long
                val termTags: String
                if (format == 1) {
                    // v1: [expression, reading, tags, rules, score, ...glossary strings]
                    glossary = JSONArray()
                    for (j in 5 until row.length()) glossary.put(row.opt(j))
                    sequence = 0
                    termTags = ""
                } else {
                    glossary = row.optJSONArray(5) ?: JSONArray()
                    sequence = row.optLong(6, 0)
                    termTags = row.optNullableString(7)
                }

                statement.clearBindings()
                statement.bindLong(1, dictId)
                statement.bindString(2, expression)
                statement.bindString(3, reading)
                statement.bindString(4, row.optNullableString(2))
                statement.bindString(5, row.optNullableString(3))
                statement.bindLong(6, row.optLong(4, 0))
                statement.bindString(7, glossary.toString())
                statement.bindLong(8, sequence)
                statement.bindString(9, termTags)
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            statement.close()
        }
        return bank.length()
    }

    private fun insertMeta(dictId: Long, bank: JSONArray): Int {
        val db = database.writableDatabase
        val statement = db.compileStatement(
            "INSERT INTO term_meta (dict_id, expression, mode, data) VALUES (?, ?, ?, ?)"
        )
        db.beginTransaction()
        try {
            for (i in 0 until bank.length()) {
                val row = bank.optJSONArray(i) ?: continue
                val expression = row.optString(0)
                val mode = row.optString(1)
                if (expression.isEmpty() || mode.isEmpty()) continue
                statement.clearBindings()
                statement.bindLong(1, dictId)
                statement.bindString(2, expression)
                statement.bindString(3, mode)
                statement.bindString(4, row.opt(2)?.toString() ?: "")
                statement.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            statement.close()
        }
        return bank.length()
    }

    private fun JSONArray.optNullableString(index: Int): String =
        if (isNull(index)) "" else optString(index)
}
