package io.github.immersionplayer.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DictionaryInfo(
    val id: Long,
    val title: String,
    val revision: String,
    val termCount: Int,
    val metaCount: Int,
    val enabled: Boolean,
    val priority: Int,
)

/** A row from a Yomitan term bank. */
data class TermRow(
    val dictionaryId: Long,
    val expression: String,
    val reading: String,
    val definitionTags: String,
    val rules: String,
    val score: Int,
    val glossaryJson: String,
    val sequence: Long,
    val termTags: String,
)

class DictionaryDatabase(context: Context) :
    SQLiteOpenHelper(context, "dictionaries.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE dictionaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                revision TEXT NOT NULL DEFAULT '',
                term_count INTEGER NOT NULL DEFAULT 0,
                meta_count INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 0,
                complete INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE terms (
                dict_id INTEGER NOT NULL,
                expression TEXT NOT NULL,
                reading TEXT NOT NULL,
                definition_tags TEXT NOT NULL,
                rules TEXT NOT NULL,
                score INTEGER NOT NULL,
                glossary TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                term_tags TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX terms_expression ON terms(expression)")
        db.execSQL("CREATE INDEX terms_reading ON terms(reading)")
        db.execSQL(
            """CREATE TABLE term_meta (
                dict_id INTEGER NOT NULL,
                expression TEXT NOT NULL,
                mode TEXT NOT NULL,
                data TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX term_meta_expression ON term_meta(expression)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun dictionaries(): List<DictionaryInfo> =
        readableDatabase.rawQuery(
            "SELECT id, title, revision, term_count, meta_count, enabled, priority FROM dictionaries " +
                "WHERE complete = 1 ORDER BY priority, id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DictionaryInfo(
                            id = c.getLong(0),
                            title = c.getString(1),
                            revision = c.getString(2),
                            termCount = c.getInt(3),
                            metaCount = c.getInt(4),
                            enabled = c.getInt(5) != 0,
                            priority = c.getInt(6),
                        )
                    )
                }
            }
        }

    fun setEnabled(id: Long, enabled: Boolean) {
        writableDatabase.update(
            "dictionaries",
            ContentValues().apply { put("enabled", if (enabled) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val args = arrayOf(id.toString())
            db.delete("terms", "dict_id = ?", args)
            db.delete("term_meta", "dict_id = ?", args)
            db.delete("dictionaries", "id = ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Removes dictionaries whose import was interrupted. */
    fun deleteIncomplete() {
        readableDatabase.rawQuery("SELECT id FROM dictionaries WHERE complete = 0", null).use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
            ids.forEach(::delete)
        }
    }

    /** Terms whose headword or reading is one of [keys], from enabled dictionaries. */
    fun findTerms(keys: Collection<String>): List<TermRow> {
        if (keys.isEmpty()) return emptyList()
        val placeholders = keys.joinToString(",") { "?" }
        val args = (keys + keys).toTypedArray()
        val sql = """
            SELECT t.dict_id, t.expression, t.reading, t.definition_tags, t.rules, t.score,
                   t.glossary, t.sequence, t.term_tags
            FROM terms t JOIN dictionaries d ON d.id = t.dict_id
            WHERE d.enabled = 1 AND (t.expression IN ($placeholders) OR t.reading IN ($placeholders))
        """
        return readableDatabase.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        TermRow(
                            dictionaryId = c.getLong(0),
                            expression = c.getString(1),
                            reading = c.getString(2),
                            definitionTags = c.getString(3),
                            rules = c.getString(4),
                            score = c.getInt(5),
                            glossaryJson = c.getString(6),
                            sequence = c.getLong(7),
                            termTags = c.getString(8),
                        )
                    )
                }
            }
        }
    }

    /** Frequency/pitch metadata rows for the given headwords: (dictId, expression, mode, data). */
    fun findMeta(expressions: Collection<String>): List<Triple<Long, String, Pair<String, String>>> {
        if (expressions.isEmpty()) return emptyList()
        val placeholders = expressions.joinToString(",") { "?" }
        val sql = """
            SELECT m.dict_id, m.expression, m.mode, m.data
            FROM term_meta m JOIN dictionaries d ON d.id = m.dict_id
            WHERE d.enabled = 1 AND m.expression IN ($placeholders)
        """
        return readableDatabase.rawQuery(sql, expressions.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Triple(c.getLong(0), c.getString(1), c.getString(2) to c.getString(3)))
                }
            }
        }
    }

    fun dictionaryTitles(): Map<Long, String> =
        dictionaries().associate { it.id to it.title }
}
