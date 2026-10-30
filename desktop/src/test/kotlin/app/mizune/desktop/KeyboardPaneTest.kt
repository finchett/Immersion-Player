package app.mizune.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.mizune.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.prefs.Preferences

/** Settings › Keyboard: setting, moving and removing keys. */
@OptIn(ExperimentalTestApi::class)
class KeyboardPaneTest {
    private val node = "app/mizune-test-keyboard"

    private fun fresh(): Settings {
        Preferences.userRoot().node(node).removeNode()
        return Settings(node)
    }

    @Test
    fun clickAKeyThenPressANewOne() = runDesktopComposeUiTest {
        val settings = fresh()
        setContent { DesktopTheme(AppTheme.Jade) { Column { PlayerKeyList(settings) } } }

        // U is only Stop at the end of every line's
        onNodeWithText("U").performClick()
        waitForIdle()
        onNodeWithText("Press a key…").assertExists()
        onNodeWithText("Press a key…").performKeyInput { pressKey(Key.B) }
        waitForIdle()
        assertEquals(listOf(KeyBinding(Key.B)), settings.keyMap.keysFor(PlayerAction.StopEveryLine))
        assertEquals(0, onAllNodesWithText("Press a key…").fetchSemanticsNodes().size)
        Preferences.userRoot().node(node).removeNode()
    }

    @Test
    fun escCancelsAndChangesNothing() = runDesktopComposeUiTest {
        val settings = fresh()
        setContent { DesktopTheme(AppTheme.Jade) { Column { PlayerKeyList(settings) } } }
        onNodeWithText("U").performClick()
        waitForIdle()
        onNodeWithText("Press a key…").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(0, onAllNodesWithText("Press a key…").fetchSemanticsNodes().size)
        assertEquals(KeyMap.DEFAULT, settings.keyMap)
        Preferences.userRoot().node(node).removeNode()
    }

    @Test
    fun aKeyAnotherActionHadMovesAndTheRowSaysSo() = runDesktopComposeUiTest {
        val settings = fresh()
        setContent { DesktopTheme(AppTheme.Jade) { Column { PlayerKeyList(settings) } } }
        onNodeWithText("U").performClick()
        waitForIdle()
        onNodeWithText("Press a key…").performKeyInput { pressKey(Key.K) }
        waitForIdle()
        assertEquals(listOf(KeyBinding(Key.K)), settings.keyMap.keysFor(PlayerAction.StopEveryLine))
        assertEquals(emptyList<KeyBinding>(), settings.keyMap.keysFor(PlayerAction.NextLineStop))
        onNodeWithText("K was ${PlayerAction.NextLineStop.label}").assertExists()
        Preferences.userRoot().node(node).removeNode()
    }

    @Test
    fun theCrossOnAKeyRemovesIt() = runDesktopComposeUiTest {
        val settings = fresh()
        setContent { DesktopTheme(AppTheme.Jade) { Column { PlayerKeyList(settings) } } }
        onNodeWithText("U").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithContentDescription("Remove").performClick()
        waitForIdle()
        assertEquals(emptyList<KeyBinding>(), settings.keyMap.keysFor(PlayerAction.StopEveryLine))
        Preferences.userRoot().node(node).removeNode()
    }
}
