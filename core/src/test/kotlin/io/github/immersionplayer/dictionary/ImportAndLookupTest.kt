package io.github.immersionplayer.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportAndLookupTest {

    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val dictionary = zip(
        "index.json" to """{"title":"Test","revision":"1","format":3}""",
        "term_bank_1.json" to """[
            ["食べる","たべる","v1","v1",0,["to eat"],1,""],
            ["食べ物","たべもの","n","",0,["food"],2,""]
        ]""",
        "term_meta_bank_1.json" to """[["食べる","freq",120]]""",
    )

    private fun database() = DictionaryDatabase { JdbcSql("jdbc:sqlite::memory:") }

    @Test
    fun importsAndDeinflects() {
        val db = database()
        val info = YomitanImporter(db).import("test.zip", {}) { ByteArrayInputStream(dictionary) }
        assertEquals("Test", info.title)
        assertEquals(2, info.termCount)
        assertEquals(1, info.metaCount)

        val result = DictionaryLookup(db).lookup("食べさせられなかった", 0)!!
        assertEquals("食べる", result.entries.first().expression)
        assertEquals("食べさせられなかった".length, result.matchLength)
        assertTrue(result.entries.first().frequencies.isNotEmpty())
    }

    @Test
    fun reimportReplacesOldCopy() {
        val db = database()
        repeat(2) { YomitanImporter(db).import("test.zip", {}) { ByteArrayInputStream(dictionary) } }
        assertEquals(1, db.dictionaries().size)
    }

    @Test
    fun failedImportLeavesNothingBehind() {
        val db = database()
        val broken = zip("readme.txt" to "not a dictionary")
        runCatching { YomitanImporter(db).import("broken.zip", {}) { ByteArrayInputStream(broken) } }
        db.deleteIncomplete()
        assertEquals(0, db.dictionaries().size)
    }
}
