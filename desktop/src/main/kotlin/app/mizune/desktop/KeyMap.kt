package app.mizune.desktop

import androidx.compose.ui.input.key.Key
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a key can do in the player. [defaults] are the keys it had before keys could be changed;
 * Settings › Keyboard changes them. Esc and ⌘/Ctrl + 0–6 (Anime4K) aren't here: they stay fixed.
 */
enum class PlayerAction(val label: String, val defaults: List<String>, val note: String? = null) {
    PlayPause("Play / pause", listOf("Space")),
    PreviousLine("Previous line, then keep playing", listOf("H", "Left")),
    NextLine("Next line, then keep playing", listOf("L", "Right"), "while paused, carries on playing first"),
    PreviousLineStop("Previous line, stop at its end", listOf("J")),
    NextLineStop("Next line, stop at its end", listOf("K"), "while paused, carries on playing first"),
    ReplayLine("Replay this line, stop at its end", listOf(";", "Down")),
    Peek("Show the peek language", listOf("I"), "hold, or hold the line"),
    SeekBack("Seek 5 s back", listOf("Y", "Shift+Left")),
    SeekForward("Seek 5 s forward", listOf("O", "Shift+Right")),
    SubtitlesEarlier("Shift subtitles 0.1 s earlier", listOf("N")),
    SubtitlesLater("Shift subtitles 0.1 s later", listOf("M")),
    StopEveryLine("Stop at the end of every line, on or off", listOf("U"), "or the button under the panel"),
    Panel("Put the panel away, or bring it back", listOf("S"), "the line then goes over the video"),
    FullScreen("Full screen", listOf("F", "F11"), "or double-click"),
    FillFit("Fill the area, or fit the whole picture", listOf("Z"), "or ⌘ + scroll"),
    AddCard("Add the looked-up word to Anki", listOf("A"), "when Anki cards are on"),
    PlayNext("Play the next video", listOf("Enter"), "at the end of one"),
}

/** A key, with the modifiers held for it. */
data class KeyBinding(
    val key: Key,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    /** As a key map file writes it: "Shift+Left", "Cmd+K", ";". */
    val name: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
            if (meta) append("Cmd+")
            append(KEY_NAMES.entries.first { it.value == key }.key)
        }

    /** As keycaps show it: the modifiers as this system writes them, then the key. */
    val caps: List<String>
        get() = buildList {
            if (ctrl) add(if (isMac) "⌃" else "Ctrl")
            if (alt) add(if (isMac) "⌥" else "Alt")
            if (shift) add(if (isMac) "⇧" else "Shift")
            if (meta) add(if (isMac) "⌘" else "Win")
            val name = KEY_NAMES.entries.first { it.value == key }.key
            add(CAP_LABELS[name] ?: name)
        }

    /** In a line of text, such as a tooltip: "⇧←", "Shift+←". */
    val label: String get() = caps.joinToString(if (isMac) "" else "+")

    /** Why this can't be bound, if it can't: the keys that stay fixed. */
    val fixed: String?
        get() = when {
            key == Key.Escape -> "Esc is kept for closing things and going back"
            (ctrl || meta) && key in ANIME4K_DIGITS -> "${if (isMac) "⌘" else "Ctrl"} + 0–6 are kept for Anime4K's modes"
            else -> null
        }

    companion object {
        /** [name] as [KeyBinding.name] writes it, or null if it isn't a key this knows. */
        fun parse(name: String): KeyBinding? {
            val parts = name.trim().split('+')
            // "+" can't be bound, so a trailing one is never the key itself
            val key = KEY_NAMES[parts.last()] ?: KEY_NAMES[parts.last().uppercase()] ?: return null
            val modifiers = parts.dropLast(1).map { it.lowercase() }
            if (modifiers.any { it !in setOf("shift", "ctrl", "alt", "cmd") }) return null
            return KeyBinding(key, "shift" in modifiers, "ctrl" in modifiers, "alt" in modifiers, "cmd" in modifiers)
        }

        fun known(key: Key) = key in KEY_NAMES.values
    }
}

/** Which keys do what in the player: up to [MAX_KEYS] per action, and no key doing two things. */
class KeyMap private constructor(private val keys: Map<PlayerAction, List<KeyBinding>>) {

    fun keysFor(action: PlayerAction): List<KeyBinding> = keys[action].orEmpty()

    /** The first key for [action] as one keycap (modifiers and all), or none if it has none. */
    fun caps(action: PlayerAction): List<String> = listOfNotNull(label(action))

    /** The first key for [action] in a line of text, or null if it has none. */
    fun label(action: PlayerAction): String? = keysFor(action).firstOrNull()?.label

    /**
     * The action for a key pressed with these modifiers. With Shift held and nothing bound to that,
     * the same key without Shift counts, as it always has (so caps lock and a held Shift don't
     * stop the player's keys working).
     */
    fun actionFor(pressed: KeyBinding): PlayerAction? =
        owner(pressed) ?: if (pressed.shift) owner(pressed.copy(shift = false)) else null

    /** The action [binding] belongs to, exactly as written. */
    fun owner(binding: KeyBinding): PlayerAction? = keys.entries.firstOrNull { binding in it.value }?.key

    /**
     * [binding] as [action]'s key number [slot] (0 or 1; past its last key, it's added), and the
     * action it was taken from, if another had it.
     */
    fun bind(action: PlayerAction, slot: Int, binding: KeyBinding): Pair<KeyMap, PlayerAction?> {
        val from = owner(binding)?.takeIf { it != action }
        val next = keys.mapValues { (a, list) -> if (a == from) list - binding else list }.toMutableMap()
        val own = next[action].orEmpty().filter { it != binding }.toMutableList()
        if (slot < own.size) own[slot] = binding else own.add(binding)
        next[action] = own.take(MAX_KEYS)
        return KeyMap(next) to from
    }

    /** [action] without its key number [slot]. */
    fun unbind(action: PlayerAction, slot: Int): KeyMap =
        KeyMap(keys + (action to keysFor(action).filterIndexed { i, _ -> i != slot }))

    val isDefault: Boolean get() = this == DEFAULT

    /** The file Export writes and Import reads. */
    fun toJson(): String = JSONObject()
        .put("mizune", "keys")
        .put("version", 1)
        .put("keys", JSONObject().apply {
            for (action in PlayerAction.entries) put(action.name, JSONArray(keysFor(action).map { it.name }))
        })
        .toString(2)

    override fun equals(other: Any?) = other is KeyMap && other.keys == keys
    override fun hashCode() = keys.hashCode()

    /** A key map read from a file, and what in it couldn't be used. */
    class Read(val map: KeyMap, val problems: List<String>)

    companion object {
        const val MAX_KEYS = 2

        val DEFAULT = KeyMap(PlayerAction.entries.associateWith { action -> action.defaults.map { KeyBinding.parse(it)!! } })

        /**
         * A key map file, or the copy kept in settings. Actions it leaves out keep their default
         * keys; anything it can't use is skipped and said in [Read.problems]: a name it doesn't know,
         * a key that stays fixed, a key already given to an action earlier in the list, a third key.
         */
        fun fromJson(text: String): Read {
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return Read(DEFAULT, listOf("This isn't a Mizune key map file"))
            val keys = json.optJSONObject("keys") ?: return Read(DEFAULT, listOf("This isn't a Mizune key map file"))
            val problems = mutableListOf<String>()
            val actions = PlayerAction.entries.associateBy { it.name }
            keys.keySet().filter { it !in actions }.sorted().forEach { problems += "Unknown action \"$it\"" }
            val taken = mutableMapOf<KeyBinding, PlayerAction>()
            val map = PlayerAction.entries.associateWith { action ->
                val names = keys.optJSONArray(action.name) ?: return@associateWith action.defaults
                    .mapNotNull { KeyBinding.parse(it) }.filter { taken.putIfAbsent(it, action) == null }
                buildList {
                    for (i in 0 until names.length()) {
                        val name = names.optString(i)
                        val binding = KeyBinding.parse(name)
                        when {
                            binding == null -> problems += "${action.label}: unknown key \"$name\""
                            binding.fixed != null -> problems += "${action.label}: ${binding.fixed}"
                            taken[binding] != null -> problems += "${action.label}: ${binding.label} is already ${taken[binding]!!.label}"
                            size == MAX_KEYS -> problems += "${action.label}: only $MAX_KEYS keys, so ${binding.label} is left out"
                            else -> { add(binding); taken[binding] = action }
                        }
                    }
                }
            }
            return Read(KeyMap(map), problems)
        }
    }
}

private val ANIME4K_DIGITS = setOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)

/** The keys a binding can use, by the names a key map file gives them. */
private val KEY_NAMES: Map<String, Key> = linkedMapOf(
    "A" to Key.A, "B" to Key.B, "C" to Key.C, "D" to Key.D, "E" to Key.E, "F" to Key.F, "G" to Key.G,
    "H" to Key.H, "I" to Key.I, "J" to Key.J, "K" to Key.K, "L" to Key.L, "M" to Key.M, "N" to Key.N,
    "O" to Key.O, "P" to Key.P, "Q" to Key.Q, "R" to Key.R, "S" to Key.S, "T" to Key.T, "U" to Key.U,
    "V" to Key.V, "W" to Key.W, "X" to Key.X, "Y" to Key.Y, "Z" to Key.Z,
    "0" to Key.Zero, "1" to Key.One, "2" to Key.Two, "3" to Key.Three, "4" to Key.Four,
    "5" to Key.Five, "6" to Key.Six, "7" to Key.Seven, "8" to Key.Eight, "9" to Key.Nine,
    ";" to Key.Semicolon, "'" to Key.Apostrophe, "," to Key.Comma, "." to Key.Period, "/" to Key.Slash,
    "\\" to Key.Backslash, "[" to Key.LeftBracket, "]" to Key.RightBracket, "-" to Key.Minus,
    "=" to Key.Equals, "`" to Key.Grave,
    "Space" to Key.Spacebar, "Enter" to Key.Enter, "Tab" to Key.Tab, "Backspace" to Key.Backspace,
    "Delete" to Key.Delete, "Escape" to Key.Escape,
    "Left" to Key.DirectionLeft, "Right" to Key.DirectionRight, "Up" to Key.DirectionUp, "Down" to Key.DirectionDown,
    "Home" to Key.MoveHome, "End" to Key.MoveEnd, "PageUp" to Key.PageUp, "PageDown" to Key.PageDown,
    "F1" to Key.F1, "F2" to Key.F2, "F3" to Key.F3, "F4" to Key.F4, "F5" to Key.F5, "F6" to Key.F6,
    "F7" to Key.F7, "F8" to Key.F8, "F9" to Key.F9, "F10" to Key.F10, "F11" to Key.F11, "F12" to Key.F12,
)

/** How a key's name reads on a keycap, where that's not the name itself. */
private val CAP_LABELS = mapOf(
    "Left" to "←", "Right" to "→", "Up" to "↑", "Down" to "↓",
    "Enter" to if (isMac) "Return" else "Enter", "Escape" to "Esc", "Backspace" to if (isMac) "⌫" else "Backspace",
    "PageUp" to "Page Up", "PageDown" to "Page Down",
)
