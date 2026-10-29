package app.mizune.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.mizune.ui.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.prefs.Preferences
import javax.imageio.ImageIO

/** Every step of the welcome renders, light and dark; MIZUNE_TEST_SHOTS keeps a picture of each. */
@OptIn(ExperimentalTestApi::class)
class OnboardingTest {
    private val shots = System.getenv("MIZUNE_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }

    @Test
    fun everyStepRenders() {
        val dir = Files.createTempDirectory("mizune-onboarding").toFile()
        File(dir, "Videos/Show").mkdirs()
        listOf("Episode 1.mkv", "Episode 2.mkv").forEach { File(dir, "Videos/Show/$it").writeText("") }
        val node = "app/mizune-test-onboarding"
        Preferences.userRoot().node(node).removeNode()
        val settings = Settings(node).apply { updateAnkiUrl("http://127.0.0.1:9") }
        // nobody who'd chosen a library before is welcomed again
        assertTrue(!settings.onboarded)
        val app = DesktopApp(File(dir, ".data").apply { mkdirs() }, File(dir, ".cache"), settings)
        val headlines = mapOf(
            OnboardingStep.Welcome to "Welcome to Mizune",
            OnboardingStep.Look to "Pick a look",
            OnboardingStep.Videos to "Where do your videos live?",
            OnboardingStep.Dictionaries to "Choose your dictionaries",
            OnboardingStep.Anki to "Make flashcards with Anki",
            OnboardingStep.Done to "You're all set!",
        )
        try {
            runDesktopComposeUiTest(width = 1100, height = 720) {
                // the mascot bobs for ever, so time moves only when told to
                mainClock.autoAdvance = false
                var step by mutableStateOf(OnboardingStep.Welcome)
                var theme by mutableStateOf(AppTheme.Celadon)
                setContent {
                    DesktopTheme(theme) {
                        key(step) { Onboarding(app, onFinish = {}, start = step) }
                    }
                }
                fun shoot(name: String) {
                    mainClock.advanceTimeBy(900)
                    shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "onboarding-$name.png")) }
                }
                OnboardingStep.entries.forEach {
                    step = it
                    if (it == OnboardingStep.Videos) settings.updateLibraryRoot(File(dir, "Videos").path)
                    mainClock.advanceTimeBy(600)
                    assertTrue(it.name, onAllNodesWithText(headlines.getValue(it)).fetchSemanticsNodes().isNotEmpty())
                    shoot(it.name.lowercase())
                }
                theme = AppTheme.Jade
                step = OnboardingStep.Dictionaries
                mainClock.advanceTimeBy(600)
                shoot("dictionaries-dark")
                step = OnboardingStep.Done
                mainClock.advanceTimeBy(100)
                shoot("done-dark")
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
