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
import io.github.immersionplayer.ui.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences
import javax.imageio.ImageIO

/** Library and settings render from a plain folder; no video playback involved. */
@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    private val shots = System.getenv("IMMERSION_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }

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

                setContent {
                    DesktopTheme(AppTheme.Oled) {
                        Chrome(header = { SettingsHeader(onBack = {}) }, body = { SettingsBody(app) })
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Dictionaries").fetchSemanticsNodes().isNotEmpty() }
                shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "settings.png")) }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
