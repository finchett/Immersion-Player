package app.mizune.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences

/** Immersion Player's settings and folders come across to Mizune the first time it runs. */
class RenameMigrationTest {
    private val old = "app/mizune-test-rename-old"
    private val new = "app/mizune-test-rename-new"

    @Test
    fun settingsCarryOverOnce() {
        val root = Preferences.userRoot()
        try {
            root.node(old).apply {
                put("library_root", "/Users/me/Anime")
                node("videos").putDouble("abc.pos", 42.0)
            }
            val prefs = root.node(new)
            Settings.carryOver(prefs, old)
            assertEquals("/Users/me/Anime", prefs.get("library_root", null))
            assertEquals(42.0, prefs.node("videos").getDouble("abc.pos", 0.0), 0.0)

            // once Mizune has settings of its own, the old ones don't come back over them
            prefs.put("library_root", "/Users/me/Films")
            Settings.carryOver(prefs, old)
            assertEquals("/Users/me/Films", prefs.get("library_root", null))
        } finally {
            root.node(old).removeNode()
            root.node(new).removeNode()
        }
    }

    @Test
    fun noOldSettingsLeavesANewStart() {
        val prefs = Preferences.userRoot().node(new)
        try {
            Settings.carryOver(prefs, "app/mizune-test-rename-missing")
            assertTrue(prefs.keys().isEmpty())
        } finally {
            prefs.removeNode()
        }
    }

    @Test
    fun theOldFolderMovesAcross() {
        val parent = Files.createTempDirectory("mizune-rename").toFile()
        try {
            File(parent, "Immersion Player").mkdirs()
            File(parent, "Immersion Player/dictionaries.db").writeText("words")
            val dir = AppDirs.movedIn(parent, "Immersion Player", "Mizune")
            assertEquals("words", File(dir, "dictionaries.db").readText())
            assertFalse(File(parent, "Immersion Player").exists())

            // a Mizune folder that's already there is kept
            File(parent, "Immersion Player").mkdirs()
            assertEquals(dir, AppDirs.movedIn(parent, "Immersion Player", "Mizune"))
            assertTrue(File(parent, "Immersion Player").exists())
            assertEquals("words", File(dir, "dictionaries.db").readText())
        } finally {
            parent.deleteRecursively()
        }
    }
}
