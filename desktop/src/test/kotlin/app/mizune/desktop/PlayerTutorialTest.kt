package app.mizune.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.mizune.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** The first-play tutorial steps through by keys and clicks; MIZUNE_TEST_SHOTS keeps pictures of each step. */
@OptIn(ExperimentalTestApi::class)
class PlayerTutorialTest {
    private val shots = System.getenv("MIZUNE_TEST_SHOTS")?.let(::File)?.apply { mkdirs() }
    private val titles = mapOf(
        TutorialStep.Words to "Look up any word",
        TutorialStep.Lines to "Move line by line",
        TutorialStep.Peek to "Peek at the translation",
        TutorialStep.Playback to "Play, pause and the rest",
    )

    @Test
    fun keysStepThroughAndEscSkips() = runDesktopComposeUiTest(width = 1100, height = 720) {
        // the pictures loop for ever, so time moves only when told to
        mainClock.autoAdvance = false
        val keys = PlayerKeys()
        var finished = 0
        var shown by mutableStateOf(true)
        setContent {
            DesktopTheme(AppTheme.Celadon) {
                if (shown) PlayerTutorial(keys, ankiEnabled = false, onFinish = { finished++; shown = false })
            }
        }
        fun press(key: Key) {
            keys.onTutorialKey!!(key)
            mainClock.advanceTimeBy(700)
        }
        mainClock.advanceTimeBy(700)
        assertTrue(onAllNodesWithText(titles.getValue(TutorialStep.Words)).fetchSemanticsNodes().isNotEmpty())
        press(Key.DirectionRight)
        assertTrue(onAllNodesWithText(titles.getValue(TutorialStep.Lines)).fetchSemanticsNodes().isNotEmpty())
        press(Key.DirectionLeft)
        assertTrue(onAllNodesWithText(titles.getValue(TutorialStep.Words)).fetchSemanticsNodes().isNotEmpty())
        // the player's own keys go nowhere while it's up
        press(Key.Spacebar)
        assertEquals(0, finished)
        press(Key.Enter)
        press(Key.Enter)
        press(Key.Enter)
        assertTrue(onAllNodesWithText("Start watching").fetchSemanticsNodes().isNotEmpty())
        press(Key.Enter)
        assertEquals(1, finished)
        // gone, it lets the keys go back to the player
        assertNull(keys.onTutorialKey)

        shown = true
        mainClock.advanceTimeBy(700)
        press(Key.Escape)
        assertEquals(2, finished)
    }

    @Test
    fun buttonsStepThrough() = runDesktopComposeUiTest(width = 1100, height = 720) {
        mainClock.autoAdvance = false
        var finished = false
        setContent {
            DesktopTheme(AppTheme.Celadon) {
                PlayerTutorial(PlayerKeys(), ankiEnabled = true, onFinish = { finished = true })
            }
        }
        mainClock.advanceTimeBy(700)
        // with Anki cards on, the word step mentions making one
        assertTrue(onAllNodesWithText("Save the word as an Anki card").fetchSemanticsNodes().isNotEmpty())
        repeat(3) {
            onNodeWithText("Next  →").performClick()
            mainClock.advanceTimeBy(700)
        }
        onNodeWithText("Start watching").performClick()
        assertTrue(finished)
    }

    @Test
    fun everyStepRenders() = runDesktopComposeUiTest(width = 1100, height = 720) {
        mainClock.autoAdvance = false
        var step by mutableStateOf(TutorialStep.Words)
        var theme by mutableStateOf(AppTheme.Celadon)
        setContent {
            DesktopTheme(theme) {
                key(step) { PlayerTutorial(PlayerKeys(), ankiEnabled = true, onFinish = {}, start = step) }
            }
        }
        fun shoot(name: String) {
            shots?.let { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(it, "tutorial-$name.png")) }
        }
        for (dark in listOf(false, true)) {
            theme = if (dark) AppTheme.Jade else AppTheme.Celadon
            TutorialStep.entries.forEach {
                step = it
                mainClock.advanceTimeBy(600)
                assertTrue(it.name, onAllNodesWithText(titles.getValue(it)).fetchSemanticsNodes().isNotEmpty())
                // part way into each picture's loop, where something's happening
                mainClock.advanceTimeBy(1600)
                shoot(it.name.lowercase() + if (dark) "-dark" else "")
            }
        }
    }
}
