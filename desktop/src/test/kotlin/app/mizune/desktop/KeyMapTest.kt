package app.mizune.desktop

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMapTest {
    private val defaults = KeyMap.DEFAULT

    @Test
    fun theDefaultsAreTheKeysThePlayerAlwaysHad() {
        assertEquals(PlayerAction.PlayPause, defaults.actionFor(KeyBinding(Key.Spacebar)))
        assertEquals(PlayerAction.PreviousLine, defaults.actionFor(KeyBinding(Key.H)))
        assertEquals(PlayerAction.PreviousLine, defaults.actionFor(KeyBinding(Key.DirectionLeft)))
        assertEquals(PlayerAction.SeekBack, defaults.actionFor(KeyBinding(Key.DirectionLeft, shift = true)))
        assertEquals(PlayerAction.ReplayLine, defaults.actionFor(KeyBinding(Key.DirectionDown)))
        assertEquals(PlayerAction.FullScreen, defaults.actionFor(KeyBinding(Key.F11)))
        // Esc and Anime4K's keys aren't the key map's
        assertNull(defaults.actionFor(KeyBinding(Key.Escape)))
    }

    @Test
    fun shiftHeldStillReachesTheKeyWithoutIt() {
        // as before: a held Shift (or caps lock's) doesn't stop L working
        assertEquals(PlayerAction.NextLine, defaults.actionFor(KeyBinding(Key.L, shift = true)))
        // but ⌘ and Ctrl aren't ignored: ⌘L isn't L
        assertNull(defaults.actionFor(KeyBinding(Key.L, meta = true)))
    }

    @Test
    fun aKeyTakenFromAnotherActionMovesOver() {
        val (map, from) = defaults.bind(PlayerAction.PlayPause, 1, KeyBinding(Key.K))
        assertEquals(PlayerAction.NextLineStop, from)
        assertEquals(listOf(KeyBinding(Key.Spacebar), KeyBinding(Key.K)), map.keysFor(PlayerAction.PlayPause))
        assertTrue(map.keysFor(PlayerAction.NextLineStop).isEmpty())
        assertEquals(PlayerAction.PlayPause, map.actionFor(KeyBinding(Key.K)))
    }

    @Test
    fun replacingAndRemovingKeys() {
        val (replaced, from) = defaults.bind(PlayerAction.PreviousLine, 0, KeyBinding(Key.B))
        assertNull(from)
        assertEquals(listOf(KeyBinding(Key.B), KeyBinding(Key.DirectionLeft)), replaced.keysFor(PlayerAction.PreviousLine))
        assertNull(replaced.actionFor(KeyBinding(Key.H)))
        val removed = replaced.unbind(PlayerAction.PreviousLine, 1)
        assertEquals(listOf(KeyBinding(Key.B)), removed.keysFor(PlayerAction.PreviousLine))
        // a third key replaces nothing and isn't kept
        val (two, _) = removed.bind(PlayerAction.PreviousLine, 1, KeyBinding(Key.C))
        val (still, _) = two.bind(PlayerAction.PreviousLine, 2, KeyBinding(Key.D))
        assertEquals(KeyMap.MAX_KEYS, still.keysFor(PlayerAction.PreviousLine).size)
    }

    @Test
    fun aKeyMapFileReadsBackTheSame() {
        val (custom, _) = defaults.bind(PlayerAction.Peek, 0, KeyBinding(Key.P, ctrl = true))
        val read = KeyMap.fromJson(custom.toJson())
        assertTrue(read.problems.isEmpty())
        assertEquals(custom, read.map)
        assertTrue(KeyMap.fromJson(defaults.toJson()).map.isDefault)
    }

    @Test
    fun anImportKeepsWhatItCanAndSaysWhatItCant() {
        val read = KeyMap.fromJson(
            """{"keys": {
                "PlayPause": ["P", "Nonsense"],
                "NextLine": ["P"],
                "Peek": ["Escape"],
                "Panel": ["Q", "W", "E"],
                "Rewind": ["R"]
            }}""",
        )
        assertEquals(listOf(KeyBinding(Key.P)), read.map.keysFor(PlayerAction.PlayPause))
        // P went to the action listed first; Esc stays fixed; only two keys an action
        assertTrue(read.map.keysFor(PlayerAction.NextLine).isEmpty())
        assertTrue(read.map.keysFor(PlayerAction.Peek).isEmpty())
        assertEquals(listOf(KeyBinding(Key.Q), KeyBinding(Key.W)), read.map.keysFor(PlayerAction.Panel))
        // actions the file leaves out keep their defaults
        assertEquals(defaults.keysFor(PlayerAction.SeekBack), read.map.keysFor(PlayerAction.SeekBack))
        assertEquals(5, read.problems.size)
        assertTrue(read.problems.any { "Rewind" in it })
        assertTrue(KeyMap.fromJson("not json").problems.isNotEmpty())
    }

    @Test
    fun namesAndFixedKeys() {
        assertEquals("Shift+Left", KeyBinding.parse("Shift+Left")!!.name)
        assertEquals(KeyBinding(Key.H), KeyBinding.parse("h"))
        assertNull(KeyBinding.parse("Hyper+H"))
        assertTrue(KeyBinding(Key.Escape).fixed != null)
        assertTrue(KeyBinding(Key.Three, meta = true).fixed != null)
        assertNull(KeyBinding(Key.Three).fixed)
    }
}
