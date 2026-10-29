package app.mizune.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** The first-play tutorial's steps, in order. */
private enum class GuideStep { Words, Lines, Peek, Video }

/**
 * The first time a video opens: four short cards over the paused player, each with a little
 * looping picture of the gesture (shared with the desktop's tutorial, there with keys). Back
 * goes back a card, and from the first leaves; Settings › Appearance can show it again.
 */
@Composable
fun GestureGuide(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(GuideStep.Words) }
    val last = step == GuideStep.entries.last()
    fun next() {
        if (step == GuideStep.entries.last()) onDismiss() else step = GuideStep.entries[step.ordinal + 1]
    }
    fun back() {
        if (step.ordinal > 0) step = GuideStep.entries[step.ordinal - 1] else onDismiss()
    }
    BackHandler(onBack = ::back)

    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            // the player underneath takes no touches while this is up
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .safeDrawingPadding().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // a phone on its side has little height: the picture goes beside the words instead of above
        val sideways = maxWidth > maxHeight && maxHeight < 560.dp
        val room = maxHeight
        val stage = scheme.primary.copy(alpha = 0.08f).compositeOver(scheme.surface)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surface,
            shadowElevation = 12.dp,
            modifier = if (sideways) Modifier.widthIn(max = 760.dp).height(min(room, 400.dp)) else Modifier.widthIn(max = 480.dp),
        ) {
            if (sideways) {
                Row {
                    Box(Modifier.weight(0.46f).fillMaxHeight().background(stage), contentAlignment = Alignment.Center) { Picture(step) }
                    Column(Modifier.weight(0.54f).fillMaxHeight()) {
                        Words(step, Modifier.weight(1f))
                        Footer(step, last, onSkip = onDismiss, onBack = ::back, onNext = ::next)
                    }
                }
            } else {
                Column {
                    Box(Modifier.fillMaxWidth().height(200.dp).background(stage), contentAlignment = Alignment.Center) { Picture(step) }
                    // the words take what height is left, scrolling if a small screen needs them to
                    Words(step, Modifier.heightIn(max = (room - 290.dp).coerceAtLeast(160.dp)))
                    Footer(step, last, onSkip = onDismiss, onBack = ::back, onNext = ::next)
                }
            }
        }
    }
}

@Composable
private fun Picture(step: GuideStep) {
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(280, delayMillis = 80)) togetherWith fadeOut(tween(160)) },
        label = "picture",
    ) { shown ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (shown) {
                GuideStep.Words -> TutorialWords(touch = true)
                GuideStep.Lines -> TutorialLines(touch = true)
                GuideStep.Peek -> TutorialPeek(touch = true)
                GuideStep.Video -> TutorialPlayback(touch = true)
            }
        }
    }
}

@Composable
private fun Words(step: GuideStep, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TutorialDots(GuideStep.entries.size, step.ordinal)
            Spacer(Modifier.weight(1f))
            Text("${step.ordinal + 1} of ${GuideStep.entries.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
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
        ) { shown -> Column { StepText(shown) } }
    }
}

@Composable
private fun ColumnScope.StepText(step: GuideStep) {
    val scheme = MaterialTheme.colorScheme
    val (title, body) = when (step) {
        GuideStep.Words -> "Tap any word" to
            "Tap a word in the line and its meaning opens below it. Drag across a few characters to look up " +
            "exactly those. Every new line arrives with a word looked up already."
        GuideStep.Lines -> "Swipe through the lines" to
            "Swipe the panel to move a line at a time, instead of hunting for what you missed."
        GuideStep.Peek -> "Hold to peek" to
            "Press and hold the line, or anywhere on the panel, and the translation appears. Let go and it's " +
            "gone, so you only look when you need to."
        GuideStep.Video -> "The video" to
            "Everything's a gesture, so nothing sits over the picture."
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, lineHeight = 21.sp)
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (step) {
            GuideStep.Words -> {}
            GuideStep.Lines -> {
                Hint("Swipe left", "The next line")
                Hint("Swipe right", "The line before")
                Hint("Double-tap", "Stop at the end of every line, or not")
                Hint("Tap", "Play or pause")
            }
            GuideStep.Peek -> Hint("Hold", "Show the translation")
            GuideStep.Video -> {
                Hint("Tap", "Play or pause")
                Hint("Double-tap edge", "5 s back or forward; keep tapping for more")
                Hint("Drag sideways", "Scrub through")
                Hint("Pinch", "Fill the area, or fit it all")
                Hint("Drag the divider", "Size the video and the panel")
            }
        }
    }
}

/** A gesture, in a chip like a key's, and what it does. */
@Composable
private fun Hint(gesture: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(116.dp)) {
            Text(
                gesture,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .background(scheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
    }
}

@Composable
private fun Footer(step: GuideStep, last: Boolean, onSkip: () -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!last) TextButton(onClick = onSkip) { Text("Skip") }
        Spacer(Modifier.weight(1f))
        if (step.ordinal > 0) TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.width(4.dp))
        Button(onClick = onNext, shape = RoundedCornerShape(12.dp)) {
            Text(if (last) "Start watching" else "Next")
        }
    }
}
