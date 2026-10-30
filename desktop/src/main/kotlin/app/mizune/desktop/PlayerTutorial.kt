package app.mizune.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.ui.TutorialDots
import app.mizune.ui.TutorialLines
import app.mizune.ui.TutorialPeek
import app.mizune.ui.TutorialPlayback
import app.mizune.ui.TutorialWords

/** The first-play tutorial's steps, in order. */
enum class TutorialStep { Words, Lines, Peek, Playback }

/**
 * The first time a video opens: four short cards over the paused player, each with a little
 * looping picture of what to do, from looking up a word to the keys worth knowing. → or Enter
 * moves on, ← goes back and Esc skips; while it's up no key reaches the player. Every shortcut
 * is in Settings › Keyboard, which can also show this again.
 */
@Composable
fun PlayerTutorial(
    keys: PlayerKeys,
    ankiEnabled: Boolean,
    onFinish: () -> Unit,
    /** The keys to show, as the user has them. */
    keyMap: KeyMap = KeyMap.DEFAULT,
    start: TutorialStep = TutorialStep.Words,
) {
    var step by remember { mutableStateOf(start) }
    fun next() {
        if (step == TutorialStep.entries.last()) onFinish() else step = TutorialStep.entries[step.ordinal + 1]
    }
    fun back() {
        if (step.ordinal > 0) step = TutorialStep.entries[step.ordinal - 1]
    }
    DisposableEffect(keys) {
        keys.onTutorialKey = { key ->
            when (key) {
                Key.DirectionRight, Key.Enter -> next()
                Key.DirectionLeft -> back()
                Key.Escape -> onFinish()
            }
        }
        onDispose { keys.onTutorialKey = null }
    }

    val scheme = MaterialTheme.colorScheme
    val last = step == TutorialStep.entries.last()
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            // the player underneath takes no clicks or hovers while this is up
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(500.dp).shadow(28.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(scheme.surface),
        ) {
            // the picture of what to do, playing on a loop
            Box(
                Modifier.fillMaxWidth().height(200.dp).background(scheme.primary.copy(alpha = 0.08f).compositeOver(scheme.surface)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(280, delayMillis = 80)) togetherWith fadeOut(tween(160)) },
                    label = "demo",
                ) { shown ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (shown) {
                            TutorialStep.Words -> TutorialWords(touch = false)
                            TutorialStep.Lines -> TutorialLines(touch = false)
                            TutorialStep.Peek -> TutorialPeek(touch = false)
                            TutorialStep.Playback -> TutorialPlayback(touch = false)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TutorialDots(TutorialStep.entries.size, step.ordinal)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${step.ordinal + 1} of ${TutorialStep.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val sign = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(300)) { sign * it / 10 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(300)) { -sign * it / 10 })
                    },
                    label = "words",
                ) { shown -> StepText(shown, ankiEnabled, keyMap) }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!last) TextAction("Skip", onClick = onFinish, modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                if (step.ordinal > 0) TextAction("‹  Back", onClick = ::back, modifier = Modifier.padding(end = 14.dp))
                Button(onClick = ::next, shape = RoundedCornerShape(10.dp), elevation = null) {
                    Text(
                        if (last) "Start watching" else "Next  →",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepText(step: TutorialStep, ankiEnabled: Boolean, keyMap: KeyMap) {
    val scheme = MaterialTheme.colorScheme
    // as tall as the tallest step, so the buttons stay put from one to the next
    Column(Modifier.fillMaxWidth().heightIn(min = 214.dp)) {
        val (title, body) = when (step) {
            TutorialStep.Words -> "Look up any word" to
                "Click a word in the line and its meaning opens beside the video. Drag across a few " +
                "characters to look up exactly those. Every new line arrives with a word looked up already."
            TutorialStep.Lines -> "Move line by line" to
                "Missed something? Jump straight back to the line instead of hunting for it."
            TutorialStep.Peek -> "Peek at the translation" to
                (keyMap.label(PlayerAction.Peek)?.let { "Hold $it, or press and hold the line" } ?: "Press and hold the line") +
                ", and the translation appears under the Japanese. " +
                "Let go and it's gone, so you only look when you need to."
            TutorialStep.Playback -> "Play, pause and the rest" to
                "A few more you'll reach for. Every shortcut is in Settings › Keyboard."
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        when (step) {
            TutorialStep.Words -> if (ankiEnabled) Hint(keyMap.caps(PlayerAction.AddCard), "Save the word as an Anki card")
            TutorialStep.Lines -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                // each action's first key, then its second, as the user has them
                fun second(action: PlayerAction) = listOfNotNull(keyMap.keysFor(action).getOrNull(1)?.label)
                Hint(keyMap.caps(PlayerAction.PreviousLine) + keyMap.caps(PlayerAction.NextLine), "The previous or next line")
                Hint(second(PlayerAction.PreviousLine) + second(PlayerAction.NextLine), "The same")
                Hint(keyMap.caps(PlayerAction.PreviousLineStop) + keyMap.caps(PlayerAction.NextLineStop), "Previous or next, stopping when it ends")
                Hint(keyMap.caps(PlayerAction.ReplayLine), "Hear this line again")
            }
            TutorialStep.Peek -> Hint(keyMap.caps(PlayerAction.Peek), "Hold to peek, let go to hide")
            TutorialStep.Playback -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Hint(keyMap.caps(PlayerAction.PlayPause), "Play or pause")
                    Hint(keyMap.caps(PlayerAction.SeekBack) + keyMap.caps(PlayerAction.SeekForward), "5 s back or forward")
                    Hint(keyMap.caps(PlayerAction.StopEveryLine), "Stop after every line")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Hint(keyMap.caps(PlayerAction.Panel), "Put the panel away")
                    Hint(keyMap.caps(PlayerAction.FullScreen), "Full screen")
                    Hint(keyMap.caps(PlayerAction.FillFit), "Fill the area, or fit it all")
                    Hint(listOf("Esc"), "Back to your library")
                }
            }
        }
    }
}

/** A key and what it does; nothing, if no key does it. */
@Composable
private fun Hint(caps: List<String>, text: String) {
    if (caps.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { caps.forEach { Keycap(it) } }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
