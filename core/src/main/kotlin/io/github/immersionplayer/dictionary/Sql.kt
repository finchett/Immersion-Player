package io.github.immersionplayer.dictionary

/**
 * The SQLite operations the dictionary needs. Android implements this over
 * android.database.sqlite; the desktop app over JDBC.
 */
interface Sql {
    fun execute(sql: String, vararg args: Any?)

    /** Runs [sql] and maps each row; [Row] is only valid inside [map]. */
    fun <T> query(sql: String, vararg args: Any?, map: (Row) -> T): List<T>

    /** Inserts a row and returns its rowid. */
    fun insert(sql: String, vararg args: Any?): Long

    /** A statement compiled once and run many times, for bulk inserts. */
    fun prepare(sql: String): Prepared

    fun <T> transaction(block: () -> T): T

    interface Row {
        fun long(index: Int): Long
        fun int(index: Int): Int
        fun string(index: Int): String
    }

    interface Prepared : AutoCloseable {
        fun execute(vararg args: Any?)
    }
}
