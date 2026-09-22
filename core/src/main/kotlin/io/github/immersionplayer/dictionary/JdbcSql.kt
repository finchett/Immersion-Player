package io.github.immersionplayer.dictionary

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/** [Sql] over JDBC, for the desktop app and tests. Needs a SQLite driver (sqlite-jdbc) at runtime. */
class JdbcSql(url: String) : Sql {

    private val connection: Connection = DriverManager.getConnection(url).apply {
        createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
    }
    private var depth = 0

    override fun execute(sql: String, vararg args: Any?) {
        synchronized(connection) { connection.prepareStatement(sql).use { it.bindAll(args); it.execute() } }
    }

    override fun <T> query(sql: String, vararg args: Any?, map: (Sql.Row) -> T): List<T> =
        synchronized(connection) {
            connection.prepareStatement(sql).use { statement ->
                statement.bindAll(args)
                statement.executeQuery().use { rs ->
                    val row = object : Sql.Row {
                        override fun long(index: Int) = rs.getLong(index + 1)
                        override fun int(index: Int) = rs.getInt(index + 1)
                        override fun string(index: Int): String = rs.getString(index + 1)
                    }
                    buildList { while (rs.next()) add(map(row)) }
                }
            }
        }

    override fun insert(sql: String, vararg args: Any?): Long =
        synchronized(connection) {
            connection.prepareStatement(sql).use { it.bindAll(args); it.executeUpdate() }
            connection.createStatement().use { s ->
                s.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) }
            }
        }

    override fun prepare(sql: String): Sql.Prepared {
        val statement = connection.prepareStatement(sql)
        return object : Sql.Prepared {
            override fun execute(vararg args: Any?) {
                synchronized(connection) {
                    statement.clearParameters()
                    statement.bindAll(args)
                    statement.executeUpdate()
                }
            }
            override fun close() = statement.close()
        }
    }

    // nested calls join the outer transaction, as on Android
    override fun <T> transaction(block: () -> T): T = synchronized(connection) {
        if (depth++ == 0) connection.autoCommit = false
        var ok = false
        try {
            block().also { ok = true }
        } finally {
            if (--depth == 0) {
                if (ok) connection.commit() else connection.rollback()
                connection.autoCommit = true
            }
        }
    }

    private fun PreparedStatement.bindAll(args: Array<out Any?>) {
        args.forEachIndexed { i, arg ->
            val index = i + 1
            when (arg) {
                null -> setObject(index, null)
                is Long -> setLong(index, arg)
                is Int -> setLong(index, arg.toLong())
                is Boolean -> setLong(index, if (arg) 1 else 0)
                is Double -> setDouble(index, arg)
                is ByteArray -> setBytes(index, arg)
                else -> setString(index, arg.toString())
            }
        }
    }
}
