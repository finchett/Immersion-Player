package app.mizune.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The little looping pictures of the first-play tutorial, the same on both apps: with `touch` a
 * finger taps, swipes and holds where the desktop's pointer clicks and its keys are pressed.
 */

/** Which step of the tutorial is showing: a bar for this one, dots for the rest. */
@Composable
fun TutorialDots(count: Int, current: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) {
            val width by animateDpAsState(if (it == current) 22.dp else 7.dp, label = "dot")
            Box(
                Modifier.size(width, 7.dp).clip(CircleShape)
                    .background(if (it <= current) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.22f)),
            )
        }
    }
}

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

/** 天気 is clicked (or tapped), and its entry opens under the line. */
@Composable
fun TutorialWords(touch: Boolean) {
    val t = loop(4200)
    val scheme = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }
    // the word's middle, and its lower right where a pointer's tip goes
    var word by remember { mutableStateOf(Offset.Zero) }
    var tip by remember { mutableStateOf(Offset.Zero) }
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
                                        // a fingertip presses just under the word, leaving it in sight
                                        word = Offset(at.x + it.size.width * 0.5f, at.y + it.size.height * 1.15f)
                                        tip = Offset(at.x + it.size.width * 0.55f, at.y + it.size.height * 0.7f)
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
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.11f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("天気", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Text("てんき", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text("weather; the elements", style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            }
        }
        if (size == Size.Zero || word == Offset.Zero) return@Box
        if (touch) {
            // a finger comes down on the word, presses, and lifts away
            val at = word - origin
            Finger(
                pressed = swell(t, 0.35f, 0.38f, 0.42f, 0.46f),
                ripple = part(t, 0.38f, 0.58f),
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((at.x - FingerSize.toPx() / 2).toInt(), (at.y - FingerSize.toPx() / 2).toInt()) }
                    .alpha(swell(t, 0.26f, 0.32f, 0.50f, 0.58f)),
            )
        } else {
            // in from the lower right, onto the word, a click, and away
            val from = Offset(size.width * 0.86f, size.height * 0.92f)
            val to = tip - origin
            val at = from + (to - from) * part(t, 0.06f, 0.36f)
            Pointer(
                ripple = part(t, 0.38f, 0.56f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(at.x.toInt(), at.y.toInt()) }
                    .alpha(swell(t, 0f, 0.06f, 0.9f, 0.98f))
                    .scale(1f - 0.12f * swell(t, 0.37f, 0.40f, 0.42f, 0.46f)),
            )
        }
    }
}

private val Lines = listOf("おはよう！", "今日はいい天気ですね。", "散歩に行こうか。")
private val LineHeight = 38.dp
private val LinesWidth = 230.dp

/**
 * The lines moving to the next and back again: by → and ← in the panel's list, with ; replaying
 * the one it's on, or with [touch] by a finger swiping across the panel, below the line.
 */
@Composable
fun TutorialLines(touch: Boolean) = if (touch) PanelSwipe() else LinesByKeys()

@Composable
private fun LinesByKeys() {
    val t = loop(4400)
    val scheme = MaterialTheme.colorScheme
    val current = if (t in 0.20f..0.48f) 2 else 1
    val top by animateDpAsState(LineHeight * current, tween(260), label = "current")
    val flash = swell(t, 0.74f, 0.77f, 0.80f, 0.92f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        Box(Modifier.width(LinesWidth)) {
            Box(
                Modifier.offset(y = top).fillMaxWidth().height(LineHeight).clip(RoundedCornerShape(9.dp))
                    .background(scheme.primary.copy(alpha = 0.16f + 0.2f * flash))
                    .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
            )
            Column {
                Lines.forEachIndexed { index, line ->
                    Box(Modifier.height(LineHeight).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
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
            DemoKey("←", swell(t, 0.46f, 0.48f, 0.52f, 0.55f) > 0.5f)
            DemoKey(";", swell(t, 0.72f, 0.74f, 0.78f, 0.81f) > 0.5f)
            DemoKey("→", swell(t, 0.18f, 0.20f, 0.24f, 0.27f) > 0.5f)
        }
    }
}

/** Each line's looked-up word, as the panel shows it under the line. */
private val Entries = mapOf(
    1 to Triple("天気", "てんき", "weather; the elements"),
    2 to Triple("散歩", "さんぽ", "a walk; a stroll"),
)
private val PanelWidth = 250.dp
private val PanelHeight = 156.dp

/**
 * The study panel, with the line along its top and the looked-up word below: a finger swipes left
 * across the panel (not the line, which is for looking words up) and the next line slides in,
 * then right and the one before comes back.
 */
@Composable
private fun PanelSwipe() {
    val t = loop(4400)
    val scheme = MaterialTheme.colorScheme
    val current = if (t in 0.28f..0.64f) 2 else 1
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.size(PanelWidth, PanelHeight).clip(shape).background(scheme.surface).border(1.dp, scheme.onSurface.copy(alpha = 0.11f), shape)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val sign = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { sign * it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { -sign * it / 2 } + fadeOut(tween(160)))
            },
            label = "line",
        ) { index ->
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    Text(Lines[index], fontSize = 17.sp, color = scheme.onSurface)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.onSurface.copy(alpha = 0.09f)))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (word, reading, meaning) = Entries.getValue(index)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(word, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(reading, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Text(meaning, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                    Box(Modifier.width(150.dp).height(6.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.08f)))
                    Box(Modifier.width(104.dp).height(6.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.08f)))
                }
            }
        }
        // a swipe left for the next line, then right for the one before, across the word below the line
        val left = part(t, 0.14f, 0.30f)
        val right = part(t, 0.50f, 0.66f)
        val first = t < 0.40f
        val start = if (first) 0.80f else 0.20f
        val x = if (first) 0.80f - 0.60f * left else 0.20f + 0.60f * right
        Swipe(
            from = PanelWidth * start,
            to = PanelWidth * x,
            y = 112.dp,
            width = PanelWidth,
            height = PanelHeight,
            modifier = Modifier.alpha(if (first) swell(t, 0.10f, 0.14f, 0.30f, 0.36f) else swell(t, 0.46f, 0.50f, 0.66f, 0.72f)),
        )
    }
}

/** Holding I, or with [touch] a finger held on the line, brings the translation up under it. */
@Composable
fun TutorialPeek(touch: Boolean) {
    val t = loop(3800)
    val scheme = MaterialTheme.colorScheme
    // a finger's hold only counts once it's long enough, as a long press does
    val held = if (touch) t in 0.30f..0.72f else t in 0.22f..0.72f
    val peek = if (touch) swell(t, 0.30f, 0.36f, 0.72f, 0.80f) else swell(t, 0.22f, 0.30f, 0.72f, 0.80f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Text("今日はいい天気ですね。", fontSize = 25.sp, color = scheme.onSurface)
            if (touch) {
                Finger(
                    pressed = swell(t, 0.18f, 0.22f, 0.72f, 0.76f),
                    ripple = 0f,
                    hold = part(t, 0.22f, 0.30f).takeIf { t < 0.72f } ?: 0f,
                    modifier = Modifier.offset(x = 134.dp, y = 8.dp).alpha(swell(t, 0.12f, 0.18f, 0.74f, 0.82f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Nice weather today, isn't it?",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.alpha(peek).offset(y = (-4).dp * (1 - peek)),
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!touch) DemoKey("I", held)
            Text(
                when {
                    held -> "holding…"
                    touch -> "press and hold"
                    else -> "hold"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (held) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.width(if (touch) 110.dp else 64.dp),
                textAlign = if (touch) TextAlign.Center else null,
            )
        }
    }
}

/**
 * A little video that Space pauses and plays again, or with [touch] that a tap pauses, a
 * double-tap at its right edge moves on 5 s and then 10 s, another tap plays, and a pinch fills
 * the area with the picture and then fits it again.
 */
@Composable
fun TutorialPlayback(touch: Boolean) = if (touch) VideoGestures() else VideoBySpace()

@Composable
private fun VideoBySpace() {
    val t = loop(4000)
    val playing = t !in 0.30f..0.70f
    val played = minOf(t, 0.30f) + maxOf(0f, t - 0.70f)
    val first = swell(t, 0.28f, 0.30f, 0.34f, 0.37f)
    val second = swell(t, 0.68f, 0.70f, 0.74f, 0.77f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VideoTile(playing, popped = first > 0.5f || second > 0.5f, progress = 0.12f + played * 0.6f, scene = null) {}
        Spacer(Modifier.height(14.dp))
        DemoKey("Space", first > 0.5f || second > 0.5f, width = 150.dp)
    }
}

@Composable
private fun VideoGestures() {
    val t = loop(7000)
    val pauseAt = 0.10f
    val playAt = 0.42f
    val playing = t !in pauseAt..playAt
    val played = minOf(t, pauseAt) + maxOf(0f, t - playAt)
    // a double-tap at the right edge jumps on, and on again with the second tap
    val skips = when {
        t >= 0.28f -> 2
        t >= 0.24f -> 1
        else -> 0
    }
    val first = swell(t, pauseAt - 0.01f, pauseAt, pauseAt + 0.02f, pauseAt + 0.04f)
    val second = swell(t, playAt - 0.01f, playAt, playAt + 0.02f, playAt + 0.04f)
    // pinched out the picture fills the area, pinched in it fits again
    val out = part(t, 0.56f, 0.68f)
    val back = part(t, 0.82f, 0.94f)
    val fill = out * (1 - back)
    VideoTile(playing, popped = first > 0.5f || second > 0.5f, progress = 0.10f + played * 0.55f + skips * 0.07f, scene = fill) {
        // what the double-tap did
        Text(
            if (skips == 2) "+10 s" else "+5 s",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp).alpha(swell(t, 0.24f, 0.25f, 0.36f, 0.40f)),
        )
        // the taps: the middle to pause, the right edge twice, the middle to play
        val edge = t in 0.18f..0.36f
        Finger(
            pressed = if (edge) swell(t, 0.23f, 0.24f, 0.245f, 0.255f) + swell(t, 0.27f, 0.28f, 0.285f, 0.295f) else maxOf(first, second),
            ripple = when {
                edge && t < 0.27f -> part(t, 0.24f, 0.30f)
                edge -> part(t, 0.28f, 0.34f)
                t < 0.3f -> part(t, pauseAt, pauseAt + 0.06f)
                else -> part(t, playAt, playAt + 0.06f)
            },
            modifier = Modifier.align(if (edge) Alignment.CenterEnd else Alignment.Center)
                .offset(x = if (edge) (-14).dp else 30.dp, y = if (edge) 6.dp else 14.dp)
                .alpha(
                    maxOf(
                        swell(t, pauseAt - 0.05f, pauseAt - 0.02f, pauseAt + 0.03f, pauseAt + 0.06f),
                        swell(t, 0.19f, 0.21f, 0.31f, 0.34f),
                        swell(t, playAt - 0.05f, playAt - 0.02f, playAt + 0.03f, playAt + 0.06f),
                    ),
                ),
        )
        // two fingers, apart for the fill and together again for the fit
        val spread = if (t < 0.75f) 12.dp + 30.dp * out else 42.dp - 30.dp * back
        val pinching = maxOf(swell(t, 0.52f, 0.56f, 0.68f, 0.72f), swell(t, 0.78f, 0.82f, 0.94f, 0.98f))
        for (sign in listOf(-1, 1)) {
            Finger(
                pressed = 1f,
                ripple = 0f,
                modifier = Modifier.align(Alignment.Center).offset(x = spread * sign, y = spread * 0.45f * sign).alpha(pinching),
            )
        }
    }
}

/**
 * The little video: a play or pause sign in the middle and its progress along the bottom. With a
 * [scene] it shows a 4:3 picture, fitted with bars at the sides at 0 and filling the area at 1.
 */
@Composable
private fun VideoTile(
    playing: Boolean,
    popped: Boolean,
    progress: Float,
    scene: Float?,
    over: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val pop by animateFloatAsState(if (popped) 1f else 0f, tween(180), label = "pop")
    Box(
        Modifier.size(184.dp, 98.dp).clip(RoundedCornerShape(12.dp))
            .background(if (scene == null) Brush.linearGradient(listOf(Color(0xFF2E4A42), Color(0xFF16231F))) else Brush.linearGradient(listOf(Color.Black, Color.Black))),
        contentAlignment = Alignment.Center,
    ) {
        if (scene != null) {
            Canvas(Modifier.fillMaxSize()) {
                // fitted, the picture is as tall as the area; filling, as wide, its top and bottom cut off
                val fit = Size(size.height * 4 / 3, size.height)
                val full = Size(size.width, size.width * 3 / 4)
                val w = fit.width + (full.width - fit.width) * scene
                val h = fit.height + (full.height - fit.height) * scene
                val left = (size.width - w) / 2
                val top = (size.height - h) / 2
                drawRect(Brush.verticalGradient(listOf(Color(0xFF4F8A79), Color(0xFF24463D)), startY = top, endY = top + h), Offset(left, top), Size(w, h))
                drawCircle(Color(0xFFF2C46D).copy(alpha = 0.95f), radius = h * 0.11f, center = Offset(left + w * 0.72f, top + h * 0.32f))
                drawPath(
                    Path().apply {
                        moveTo(left, top + h * 0.78f)
                        quadraticTo(left + w * 0.35f, top + h * 0.56f, left + w * 0.62f, top + h * 0.74f)
                        quadraticTo(left + w * 0.82f, top + h * 0.86f, left + w, top + h * 0.66f)
                        lineTo(left + w, top + h)
                        lineTo(left, top + h)
                        close()
                    },
                    Color(0xFF16302A),
                )
            }
        }
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
            Box(Modifier.fillMaxWidth(progress.coerceAtMost(1f)).height(3.dp).background(primary.copy(alpha = 0.9f).compositeOver(Color.White)))
        }
        over()
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

private val FingerSize = 34.dp

/**
 * A fingertip seen from above: it shrinks a little while [pressed], a ring spreads from it as a
 * tap lands ([ripple]), and an arc fills round it while a hold builds ([hold]).
 */
@Composable
private fun Finger(pressed: Float, ripple: Float, modifier: Modifier, hold: Float = 0f) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(FingerSize)) {
        val r = size.minDimension / 2
        if (ripple in 0.01f..0.99f) {
            drawCircle(primary.copy(alpha = 0.45f * (1 - ripple)), radius = r * (0.9f + 1.1f * ripple), style = Stroke(width = 2.dp.toPx()))
        }
        val scale = 1f - 0.14f * pressed.coerceIn(0f, 1f)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = r * scale, center = center + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.92f), radius = r * scale)
        drawCircle(primary.copy(alpha = 0.35f + 0.4f * pressed.coerceIn(0f, 1f)), radius = r * scale * 0.42f)
        if (hold > 0f) {
            val inset = 1.5f.dp.toPx()
            drawArc(
                primary,
                startAngle = -90f,
                sweepAngle = 360f * hold,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/** A finger drawn across a [width] × [height] area from [from] to [to] at [y], with a fading trail behind it. */
@Composable
private fun Swipe(from: Dp, to: Dp, y: Dp, width: Dp, height: Dp, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Canvas(Modifier.width(width).height(height)) {
            val a = Offset(from.toPx(), y.toPx())
            val b = Offset(to.toPx(), y.toPx())
            drawLine(
                Brush.linearGradient(listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0.5f)), start = a, end = b),
                a,
                b,
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Finger(
            pressed = 1f,
            ripple = 0f,
            modifier = Modifier.offset(x = to - FingerSize / 2, y = y - FingerSize / 2),
        )
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
