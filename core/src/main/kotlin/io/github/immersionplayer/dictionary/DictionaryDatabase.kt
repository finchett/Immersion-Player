package io.github.immersionplayer.dictionary

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

class DictionaryDatabase(connect: () -> Sql) {

    // opened on first use, so constructing this on the main thread costs nothing
    private val sql: Sql by lazy { connect().also(::createSchema) }

    private fun createSchema(sql: Sql) {
        sql.execute(
            """CREATE TABLE IF NOT EXISTS dictionaries (
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
        sql.execute(
            """CREATE TABLE IF NOT EXISTS terms (
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
        sql.execute("CREATE INDEX IF NOT EXISTS terms_expression ON terms(expression)")
        sql.execute("CREATE INDEX IF NOT EXISTS terms_reading ON terms(reading)")
        sql.execute(
            """CREATE TABLE IF NOT EXISTS term_meta (
                dict_id INTEGER NOT NULL,
                expression TEXT NOT NULL,
                mode TEXT NOT NULL,
                data TEXT NOT NULL
            )"""
        )
        sql.execute("CREATE INDEX IF NOT EXISTS term_meta_expression ON term_meta(expression)")
    }

    /** For the importer, which writes terms in bulk. */
    internal val connection: Sql get() = sql

    fun dictionaries(): List<DictionaryInfo> =
        sql.query(
            "SELECT id, title, revision, term_count, meta_count, enabled, priority FROM dictionaries " +
                "WHERE complete = 1 ORDER BY priority, id",
        ) { c ->
            DictionaryInfo(
                id = c.long(0),
                title = c.string(1),
                revision = c.string(2),
                termCount = c.int(3),
                metaCount = c.int(4),
                enabled = c.int(5) != 0,
                priority = c.int(6),
            )
        }

    fun setEnabled(id: Long, enabled: Boolean) {
        sql.execute("UPDATE dictionaries SET enabled = ? WHERE id = ?", if (enabled) 1 else 0, id)
    }

    /** Moves a dictionary up (-1) or down (+1) in result order. */
    fun move(id: Long, direction: Int) {
        val ordered = dictionaries().map { it.id }.toMutableList()
        val from = ordered.indexOf(id)
        val to = from + direction
        if (from < 0 || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        sql.transaction {
            ordered.forEachIndexed { priority, dictId ->
                sql.execute("UPDATE dictionaries SET priority = ? WHERE id = ?", priority, dictId)
            }
        }
    }

    fun delete(id: Long) {
        sql.transaction {
            sql.execute("DELETE FROM terms WHERE dict_id = ?", id)
            sql.execute("DELETE FROM term_meta WHERE dict_id = ?", id)
            sql.execute("DELETE FROM dictionaries WHERE id = ?", id)
        }
    }

    /** Removes dictionaries whose import was interrupted. */
    fun deleteIncomplete() {
        sql.query("SELECT id FROM dictionaries WHERE complete = 0") { it.long(0) }.forEach(::delete)
    }

    /** Terms whose headword or reading is one of [keys], from enabled dictionaries. */
    fun findTerms(keys: Collection<String>): List<TermRow> {
        if (keys.isEmpty()) return emptyList()
        val placeholders = keys.joinToString(",") { "?" }
        val args = (keys + keys).toTypedArray()
        val query = """
            SELECT t.dict_id, t.expression, t.reading, t.definition_tags, t.rules, t.score,
                   t.glossary, t.sequence, t.term_tags
            FROM terms t JOIN dictionaries d ON d.id = t.dict_id
            WHERE d.enabled = 1 AND (t.expression IN ($placeholders) OR t.reading IN ($placeholders))
        """
        return sql.query(query, *args) { c ->
            TermRow(
                dictionaryId = c.long(0),
                expression = c.string(1),
                reading = c.string(2),
                definitionTags = c.string(3),
                rules = c.string(4),
                score = c.int(5),
                glossaryJson = c.string(6),
                sequence = c.long(7),
                termTags = c.string(8),
            )
        }
    }

    /** Frequency/pitch metadata rows for the given headwords: (dictId, expression, mode, data). */
    fun findMeta(expressions: Collection<String>): List<Triple<Long, String, Pair<String, String>>> {
        if (expressions.isEmpty()) return emptyList()
        val placeholders = expressions.joinToString(",") { "?" }
        val query = """
            SELECT m.dict_id, m.expression, m.mode, m.data
            FROM term_meta m JOIN dictionaries d ON d.id = m.dict_id
            WHERE d.enabled = 1 AND m.expression IN ($placeholders)
        """
        return sql.query(query, *expressions.toTypedArray()) { c ->
            Triple(c.long(0), c.string(1), c.string(2) to c.string(3))
        }
    }

    fun dictionaryTitles(): Map<Long, String> =
        dictionaries().associate { it.id to it.title }
}
