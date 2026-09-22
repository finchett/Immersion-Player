package io.github.immersionplayer.dictionary

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Imports Yomitan/Yomichan dictionary zips (term banks and term meta banks). */
class YomitanImporter(private val database: DictionaryDatabase) {
    private val sql = database.connection

    data class Progress(val file: String, val terms: Int, val meta: Int)

    class ImportException(message: String) : Exception(message)

    fun import(fileName: String, onProgress: (Progress) -> Unit, open: () -> InputStream): DictionaryInfo {
        val dictId = sql.insert(
            "INSERT INTO dictionaries (title, priority) VALUES (?, ?)",
            fileName.removeSuffix(".zip"),
            nextPriority(),
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
        sql.query("SELECT id FROM dictionaries WHERE title = ? AND id != ?", finalTitle, dictId) { it.long(0) }
            .forEach(database::delete)

        sql.execute(
            "UPDATE dictionaries SET title = ?, revision = ?, term_count = ?, meta_count = ?, complete = 1 WHERE id = ?",
            finalTitle, revision, termCount, metaCount, dictId,
        )
        return database.dictionaries().first { it.id == dictId }
    }

    private fun nextPriority(): Int =
        sql.query("SELECT COALESCE(MAX(priority), -1) + 1 FROM dictionaries") { it.int(0) }.firstOrNull() ?: 0

    private fun insertTerms(dictId: Long, bank: JSONArray, format: Int): Int {
        sql.transaction {
            sql.prepare(
                "INSERT INTO terms (dict_id, expression, reading, definition_tags, rules, score, glossary, sequence, term_tags) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
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

                    statement.execute(
                        dictId, expression, reading,
                        row.optNullableString(2), row.optNullableString(3), row.optLong(4, 0),
                        glossary.toString(), sequence, termTags,
                    )
                }
            }
        }
        return bank.length()
    }

    private fun insertMeta(dictId: Long, bank: JSONArray): Int {
        sql.transaction {
            sql.prepare("INSERT INTO term_meta (dict_id, expression, mode, data) VALUES (?, ?, ?, ?)").use { statement ->
                for (i in 0 until bank.length()) {
                    val row = bank.optJSONArray(i) ?: continue
                    val expression = row.optString(0)
                    val mode = row.optString(1)
                    if (expression.isEmpty() || mode.isEmpty()) continue
                    statement.execute(dictId, expression, mode, row.opt(2)?.toString() ?: "")
                }
            }
        }
        return bank.length()
    }

    private fun JSONArray.optNullableString(index: Int): String =
        if (isNull(index)) "" else optString(index)
}
