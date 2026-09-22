package io.github.immersionplayer.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteProgram

/** [Sql] over the platform SQLite. The schema itself is created by [DictionaryDatabase]. */
class AndroidSql(context: Context, name: String) : Sql {

    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.enableWriteAheadLogging()
        }
        override fun onCreate(db: SQLiteDatabase) = Unit
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val db: SQLiteDatabase get() = helper.writableDatabase

    override fun execute(sql: String, vararg args: Any?) {
        db.compileStatement(sql).use { it.bindAll(args); it.execute() }
    }

    override fun <T> query(sql: String, vararg args: Any?, map: (Sql.Row) -> T): List<T> =
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            val row = object : Sql.Row {
                override fun long(index: Int) = c.getLong(index)
                override fun int(index: Int) = c.getInt(index)
                override fun string(index: Int): String = c.getString(index)
            }
            buildList { while (c.moveToNext()) add(map(row)) }
        }

    override fun insert(sql: String, vararg args: Any?): Long =
        db.compileStatement(sql).use { it.bindAll(args); it.executeInsert() }

    override fun prepare(sql: String): Sql.Prepared {
        val statement = db.compileStatement(sql)
        return object : Sql.Prepared {
            override fun execute(vararg args: Any?) {
                statement.clearBindings()
                statement.bindAll(args)
                statement.executeInsert()
            }
            override fun close() = statement.close()
        }
    }

    override fun <T> transaction(block: () -> T): T {
        val db = db
        db.beginTransaction()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    private fun SQLiteProgram.bindAll(args: Array<out Any?>) {
        args.forEachIndexed { i, arg ->
            val index = i + 1
            when (arg) {
                null -> bindNull(index)
                is Long -> bindLong(index, arg)
                is Int -> bindLong(index, arg.toLong())
                is Boolean -> bindLong(index, if (arg) 1 else 0)
                is Double -> bindDouble(index, arg)
                is ByteArray -> bindBlob(index, arg)
                else -> bindString(index, arg.toString())
            }
        }
    }
}
