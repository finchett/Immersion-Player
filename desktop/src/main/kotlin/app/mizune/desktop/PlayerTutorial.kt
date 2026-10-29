package app.mizune.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                            TutorialStep.Words -> WordsDemo()
                            TutorialStep.Lines -> LinesDemo()
                            TutorialStep.Peek -> PeekDemo()
                            TutorialStep.Playback -> PlaybackDemo()
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dots(step)
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
                ) { shown -> StepText(shown, ankiEnabled) }
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
private fun StepText(step: TutorialStep, ankiEnabled: Boolean) {
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
                "Hold I, or press and hold the line, and the translation appears under the Japanese. " +
                "Let go and it's gone, so you only look when you need to."
            TutorialStep.Playback -> "Play, pause and the rest" to
                "A few more you'll reach for. Every shortcut is in Settings › Keyboard."
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
        when (step) {
            TutorialStep.Words -> if (ankiEnabled) Hint(listOf("A"), "Save the word as an Anki card")
            TutorialStep.Lines -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Hint(listOf("←", "→"), "The previous or next line")
                Hint(listOf("H", "L"), "The same, on the home row")
                Hint(listOf("J", "K"), "Previous or next, stopping when it ends")
                Hint(listOf(";"), "Hear this line again")
            }
            TutorialStep.Peek -> Hint(listOf("I"), "Hold to peek, let go to hide")
            TutorialStep.Playback -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Hint(listOf("Space"), "Play or pause")
                    Hint(listOf("Y", "O"), "5 s back or forward")
                    Hint(listOf("U"), "Stop after every line")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Hint(listOf("S"), "Put the panel away")
                    Hint(listOf("F"), "Full screen")
                    Hint(listOf("Z"), "Fill the area, or fit it all")
                    Hint(listOf("Esc"), "Back to your library")
                }
            }
        }
    }
}

@Composable
private fun Hint(caps: List<String>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { caps.forEach { Keycap(it) } }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Dots(step: TutorialStep) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TutorialStep.entries.forEach {
            val width by animateDpAsState(if (it == step) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier.size(width, 7.dp).clip(CircleShape)
                    .background(if (it.ordinal <= step.ordinal) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.22f)),
            )
        }
    }
}

// ---- the pictures ----

/** Where a looping picture is, 0 to 1, over [millis]. */
@Composable
private fun loop(millis: Int): Float {
    val t by rememberInfiniteTransition(label = "loop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing)),
        label = "t",
    )
    return t
}

/** How far [t] is from [from] to [to], eased, held at 0 before and 1 after. */
private fun part(t: Float, from: Float, to: Float) = FastOutSlowInEasing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

/** Up over [from]–[peak], held, then back down over [fall]–[to]. */
private fun swell(t: Float, from: Float, peak: Float, fall: Float, to: Float) = part(t, from, peak) * (1 - part(t, fall, to))

private val DemoLine = listOf("今日は", "いい", "天気", "ですね")

/** A pointer comes in, clicks 天気, and its entry opens under the line. */
@Composable
private fun WordsDemo() {
    val t = loop(4200)
    val scheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }
    var word by remember { mutableStateOf(Offset.Zero) }
    val shown = swell(t, 0.40f, 0.48f, 0.88f, 0.96f)
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            origin = it.positionInRoot()
            size = Size(it.size.width.toFloat(), it.size.height.toFloat())
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                DemoLine.forEachIndexed { index, part ->
                    val looked = index == 2
                    Text(
                        part,
                        fontSize = 26.sp,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (looked) scheme.primary.copy(alpha = 0.3f * shown) else Color.Transparent)
                            .then(
                                if (looked) {
                                    Modifier.onGloballyPositioned {
                                        val at = it.positionInRoot()
                                        word = Offset(at.x + it.size.width * 0.55f, at.y + it.size.height * 0.7f)
                                    }
                                } else Modifier,
                            )
                            .padding(horizontal = 1.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // the entry, as the panel shows it
            Column(
                Modifier.alpha(shown).offset(y = 6.dp * (1 - shown))
                    .clip(RoundedCornerShape(10.dp)).background(scheme.surface)
                    .border(1.dp, scheme.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("天気", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Text("てんき", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text("weather; the elements", style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            }
        }
        if (size != Size.Zero && word != Offset.Zero) {
            // in from the lower right, onto the word, a click, and away
            val from = Offset(size.width * 0.86f, size.height * 0.92f)
            val to = word - origin
            val move = part(t, 0.06f, 0.36f)
            val at = from + (to - from) * move
            val click = part(t, 0.38f, 0.56f)
            val press = swell(t, 0.37f, 0.40f, 0.42f, 0.46f)
            Pointer(
                ripple = click,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(at.x.toInt(), at.y.toInt()) }
                    .alpha(swell(t, 0f, 0.06f, 0.9f, 0.98f))
                    .scale(1f - 0.12f * press),
            )
        }
    }
}

/** An arrow pointer whose tip is at its top-left corner, with a ring spreading from a click. */
@Composable
private fun Pointer(ripple: Float, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(22.dp, 26.dp)) {
        if (ripple in 0.01f..0.99f) {
            drawCircle(primary.copy(alpha = 0.5f * (1 - ripple)), radius = 6.dp.toPx() + 16.dp.toPx() * ripple, center = Offset.Zero)
        }
        val u = 1.dp.toPx()
        val arrow = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 17 * u)
            lineTo(4.5f * u, 13 * u)
            lineTo(7.5f * u, 20 * u)
            lineTo(10.2f * u, 18.8f * u)
            lineTo(7.2f * u, 12.2f * u)
            lineTo(12.8f * u, 12.2f * u)
            close()
        }
        drawPath(arrow, Color.White)
        drawPath(arrow, Color(0xFF1C1C1C), style = Stroke(width = 1.3f * u))
    }
}

private val Lines = listOf("おはよう！", "今日はいい天気ですね。", "散歩に行こうか。")

/** The panel's lines, with → moving to the next, ← back again, and ; replaying the one it's on. */
@Composable
private fun LinesDemo() {
    val t = loop(4400)
    val scheme = MaterialTheme.colorScheme
    val right = swell(t, 0.18f, 0.20f, 0.24f, 0.27f)
    val left = swell(t, 0.46f, 0.48f, 0.52f, 0.55f)
    val replay = swell(t, 0.72f, 0.74f, 0.78f, 0.81f)
    val current = if (t in 0.2f..0.48f) 2 else 1
    val lineHeight = 38.dp
    val top by animateDpAsState(lineHeight * current, tween(260), label = "current")
    val flash = swell(t, 0.74f, 0.77f, 0.80f, 0.92f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        Box(Modifier.width(230.dp)) {
            Box(
                Modifier.offset(y = top).fillMaxWidth().height(lineHeight).clip(RoundedCornerShape(9.dp))
                    .background(scheme.primary.copy(alpha = 0.16f + 0.2f * flash))
                    .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
            )
            Column {
                Lines.forEachIndexed { index, line ->
                    Box(Modifier.height(lineHeight).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            line,
                            fontSize = 16.sp,
                            color = if (index == current) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.65f),
                            fontWeight = if (index == current) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            // replaying: a little ↻ by the line
            Text(
                "↻",
                fontSize = 16.sp,
                color = scheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = top + 8.dp).alpha(flash),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoKey("←", left > 0.5f)
            DemoKey(";", replay > 0.5f)
            DemoKey("→", right > 0.5f)
        }
    }
}

/** Holding I brings the translation up under the line; letting go takes it away. */
@Composable
private fun PeekDemo() {
    val t = loop(3800)
    val scheme = MaterialTheme.colorScheme
    val held = t in 0.22f..0.72f
    val peek = swell(t, 0.22f, 0.30f, 0.72f, 0.80f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("今日はいい天気ですね。", fontSize = 25.sp, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nice weather today, isn't it?",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.alpha(peek).offset(y = (-4).dp * (1 - peek)),
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoKey("I", held)
            Text(
                if (held) "holding…" else "hold",
                style = MaterialTheme.typography.labelMedium,
                color = if (held) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

/** Space pauses the picture and plays it again; the bar moves only while it plays. */
@Composable
private fun PlaybackDemo() {
    val t = loop(4000)
    val scheme = MaterialTheme.colorScheme
    val first = swell(t, 0.28f, 0.30f, 0.34f, 0.37f)
    val second = swell(t, 0.68f, 0.70f, 0.74f, 0.77f)
    val playing = t !in 0.30f..0.70f
    val played = minOf(t, 0.30f) + maxOf(0f, t - 0.70f)
    val progress = 0.12f + played * 0.6f
    val pop by animateFloatAsState(if (first > 0.5f || second > 0.5f) 1f else 0f, tween(180), label = "pop")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(184.dp, 98.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2E4A42), Color(0xFF16231F)))),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(44.dp).scale(1f + 0.12f * pop)) {
                drawCircle(Color.White.copy(alpha = 0.18f + 0.1f * pop))
                val u = size.width / 44f
                if (playing) {
                    // pause: two bars
                    drawRoundRect(Color.White, Offset(15 * u, 13 * u), Size(5 * u, 18 * u), CornerRadius(1.5f * u))
                    drawRoundRect(Color.White, Offset(24 * u, 13 * u), Size(5 * u, 18 * u), CornerRadius(1.5f * u))
                } else {
                    drawPath(
                        Path().apply {
                            moveTo(17 * u, 12 * u)
                            lineTo(32 * u, 22 * u)
                            lineTo(17 * u, 32 * u)
                            close()
                        },
                        Color.White,
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(scheme.primary.copy(alpha = 0.9f).compositeOver(Color.White)))
            }
        }
        Spacer(Modifier.height(14.dp))
        DemoKey("Space", first > 0.5f || second > 0.5f, width = 150.dp)
    }
}

/** A key seen from the front, which sinks and takes the accent colour while it's pressed. */
@Composable
private fun DemoKey(text: String, pressed: Boolean, width: Dp = 46.dp) {
    val scheme = MaterialTheme.colorScheme
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "press")
    val light = scheme.surface.luminance() > 0.5f
    val face = if (light) Color.White else scheme.onSurface.copy(alpha = 0.12f).compositeOver(scheme.surface)
    val edge = scheme.onSurface.copy(alpha = if (light) 0.16f else 0.3f)
    val shape = RoundedCornerShape(9.dp)
    Box(Modifier.size(width, 49.dp)) {
        // the key's side, showing below its top until it's pressed down
        Box(Modifier.fillMaxWidth().height(45.dp).offset(y = 4.dp).clip(shape).background(edge))
        Box(
            Modifier.fillMaxWidth().height(45.dp).offset(y = 3.dp * press).clip(shape)
                .background(lerp(face, scheme.primary, press))
                .border(1.dp, lerp(edge, scheme.primary, press), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = lerp(scheme.onSurface, scheme.onPrimary, press))
        }
    }
}
