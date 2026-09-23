package io.github.immersionplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.ui.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Library and settings render from a plain folder; no video playback involved. */
@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    private val shots = System.getenv("IMMERSION_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }

    /** A minimal Yomitan zip, so the dictionaries pane has something installed to show. */
    private fun dictionaryZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            put("index.json", """{"title":"Test dictionary","revision":"1","format":3}""")
            put("term_bank_1.json", """[["今日","きょう","n","",0,["today"],1,""]]""")
        }
        return out.toByteArray()
    }

    @Test
    fun libraryListsFoldersAndEpisodesInNaturalOrder() {
        val dir = Files.createTempDirectory("immersion-library").toFile()
        val show = File(dir, "Lectures").apply { mkdirs() }
        listOf("Lesson 1.mkv", "Lesson 2.mkv", "Lesson 10.mkv", "notes.txt").forEach { File(show, it).writeText("") }
        // with a real video, Lesson 1 also gets a thumbnail
        val video = System.getenv("IMMERSION_TEST_VIDEO")?.let(::File)?.takeIf { it.isFile }
        if (video != null) {
            File(show, "Lesson 1.mkv").delete()
            Files.createSymbolicLink(File(show, "Lesson 1.mkv").toPath(), video.absoluteFile.toPath())
        }
        File(dir, "Other").mkdirs()
        val node = "io/github/immersionplayer-test-screens"
        Preferences.userRoot().node(node).removeNode()
        val settings = Settings(node).apply { updateLibraryRoot(dir.path) }
        val app = DesktopApp(File(dir, ".data").apply { mkdirs() }, File(dir, ".cache"), settings)
        settings.savePosition(File(show, "Lesson 2.mkv").absolutePath, 600.0, 1400.0)
        YomitanImporter(app.database).import("Test dictionary.zip", {}) { dictionaryZip().inputStream() }
        try {
            runDesktopComposeUiTest(width = 1100, height = 640) {
                var folder by mutableStateOf<File?>(null)
                setContent {
                    DesktopTheme(AppTheme.Midnight) {
                        Chrome(
                            header = { LibraryHeader(app, folder, onOpenFolder = { folder = it }, onOpenSettings = {}) },
                            body = { LibraryBody(app, folder, onOpenFolder = { folder = it }, onOpenVideo = {}) },
                        )
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Lectures").fetchSemanticsNodes().isNotEmpty() }
                folder = show
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Lesson 10").fetchSemanticsNodes().isNotEmpty() }
                val order = onAllNodesWithText("Lesson", substring = true).fetchSemanticsNodes()
                    .map { it.config.toString() }
                assertTrue(order.indexOfFirst { "Lesson 2" in it } < order.indexOfFirst { "Lesson 10" in it })
                assertTrue(onAllNodesWithText("notes").fetchSemanticsNodes().isEmpty())
                if (video != null) {
                    waitUntil(timeoutMillis = 20_000) { app.thumbnails.versions.containsKey(File(show, "Lesson 1.mkv").path) }
                    waitForIdle()
                }
                shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "library.png")) }

                // settings: every pane, in the theme it was designed in and in a dark one
                var pane by mutableStateOf(SettingsPane.General)
                var theme by mutableStateOf(AppTheme.Paper)
                setContent {
                    DesktopTheme(theme) {
                        Chrome(
                            header = { SettingsHeader(pane) },
                            body = { SettingsBody(app, pane) },
                            leading = { SettingsSidebar(pane, onPane = { pane = it }, onBack = {}) },
                        )
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Media folder").fetchSemanticsNodes().isNotEmpty() }
                SettingsPane.entries.forEach { entry ->
                    pane = entry
                    waitForIdle()
                    Thread.sleep(150)
                    waitForIdle()
                    shots?.let {
                        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "settings-${entry.name.lowercase()}.png"))
                    }
                }
                // the sidebar switches panes, and the keys pane really is the keys
                assertTrue(onAllNodesWithText("Full screen").fetchSemanticsNodes().isNotEmpty())
                pane = SettingsPane.Dictionaries
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Test dictionary").fetchSemanticsNodes().isNotEmpty() }
                shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "settings-dictionaries.png")) }

                // Anki: off by default; on, it reports whether AnkiConnect answers (a live Anki
                // in IMMERSION_TEST_ANKI, else a port nothing listens on)
                pane = SettingsPane.Anki
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Make Anki cards").fetchSemanticsNodes().isNotEmpty() }
                assertTrue(!settings.ankiEnabled)
                val ankiUrl = System.getenv("IMMERSION_TEST_ANKI")
                settings.updateAnkiUrl(ankiUrl ?: "http://127.0.0.1:9")
                settings.updateAnkiEnabled(true)
                val expected = if (ankiUrl != null) "Fields" else "2055492159"
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(expected, substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "settings-anki.png")) }
                pane = SettingsPane.Appearance
                theme = AppTheme.Midnight
                waitForIdle()
                Thread.sleep(150)
                waitForIdle()
                assertTrue(onAllNodesWithText("Rounded corners").fetchSemanticsNodes().isNotEmpty())
                shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "settings-dark.png")) }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
