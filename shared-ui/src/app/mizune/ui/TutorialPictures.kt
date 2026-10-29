package app.mizune.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
 * The panel's lines, moving to the next and back again: by → and ←, then ; replaying the one
 * it's on, or with [touch] by a finger swiping the panel left and then right.
 */
@Composable
fun TutorialLines(touch: Boolean) {
    val t = loop(4400)
    val scheme = MaterialTheme.colorScheme
    // when the line changes, and what's pressing or swiping meanwhile
    val forward = if (touch) 0.28f else 0.20f
    val backward = if (touch) 0.64f else 0.48f
    val current = if (t in forward..backward) 2 else 1
    val top by animateDpAsState(LineHeight * current, tween(260), label = "current")
    val flash = if (touch) 0f else swell(t, 0.74f, 0.77f, 0.80f, 0.92f)
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
            if (touch) {
                // a swipe left for the next line, then right for the one before
                val left = part(t, 0.14f, 0.30f)
                val right = part(t, 0.50f, 0.66f)
                val swiping = t < 0.40f
                val x = if (swiping) 0.78f - 0.56f * left else 0.22f + 0.56f * right
                val start = if (swiping) 0.78f else 0.22f
                val alpha = if (swiping) swell(t, 0.10f, 0.14f, 0.30f, 0.36f) else swell(t, 0.46f, 0.50f, 0.66f, 0.72f)
                Swipe(
                    from = LinesWidth * start,
                    to = LinesWidth * x,
                    y = LineHeight * 1.5f,
                    modifier = Modifier.alpha(alpha),
                )
            } else {
                // replaying: a little ↻ by the line
                Text(
                    "↻",
                    fontSize = 16.sp,
                    color = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = top + 8.dp).alpha(flash),
                )
            }
        }
        if (!touch) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoKey("←", swell(t, 0.46f, 0.48f, 0.52f, 0.55f) > 0.5f)
                DemoKey(";", swell(t, 0.72f, 0.74f, 0.78f, 0.81f) > 0.5f)
                DemoKey("→", swell(t, 0.18f, 0.20f, 0.24f, 0.27f) > 0.5f)
            }
        }
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
 * double-tap at its right edge moves on 5 s and then 10 s, and another tap plays.
 */
@Composable
fun TutorialPlayback(touch: Boolean) {
    val t = loop(if (touch) 4800 else 4000)
    val scheme = MaterialTheme.colorScheme
    val pauseAt = if (touch) 0.22f else 0.30f
    val playAt = if (touch) 0.84f else 0.70f
    val playing = t !in pauseAt..playAt
    val played = minOf(t, pauseAt) + maxOf(0f, t - playAt)
    // a double-tap at the right edge jumps on, and on again with the second tap
    val skips = when {
        !touch -> 0
        t >= 0.62f -> 2
        t >= 0.56f -> 1
        else -> 0
    }
    val progress = 0.12f + played * 0.6f + skips * 0.07f
    val first = swell(t, pauseAt - 0.02f, pauseAt, pauseAt + 0.04f, pauseAt + 0.07f)
    val second = swell(t, playAt - 0.02f, playAt, playAt + 0.04f, playAt + 0.07f)
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
            if (touch) {
                // what the double-tap did, by the edge it was tapped at
                Text(
                    if (skips == 2) "+10 s" else "+5 s",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp)
                        .alpha(swell(t, 0.56f, 0.58f, 0.74f, 0.80f)),
                )
                // the taps: the middle to pause, the right edge twice, the middle to play
                val edge = t in 0.48f..0.72f
                Finger(
                    pressed = if (edge) swell(t, 0.54f, 0.56f, 0.57f, 0.59f) + swell(t, 0.60f, 0.62f, 0.63f, 0.65f)
                    else maxOf(first, second),
                    ripple = when {
                        edge && t < 0.60f -> part(t, 0.56f, 0.68f)
                        edge -> part(t, 0.62f, 0.72f)
                        t < 0.5f -> part(t, pauseAt, pauseAt + 0.12f)
                        else -> part(t, playAt, playAt + 0.12f)
                    },
                    modifier = Modifier.align(if (edge) Alignment.CenterEnd else Alignment.Center)
                        .offset(x = if (edge) (-14).dp else 30.dp, y = if (edge) 6.dp else 14.dp)
                        .alpha(
                            maxOf(
                                swell(t, pauseAt - 0.08f, pauseAt - 0.04f, pauseAt + 0.06f, pauseAt + 0.12f),
                                swell(t, 0.48f, 0.52f, 0.66f, 0.72f),
                                swell(t, playAt - 0.08f, playAt - 0.04f, playAt + 0.06f, playAt + 0.12f),
                            ),
                        ),
                )
            }
        }
        if (!touch) {
            Spacer(Modifier.height(14.dp))
            DemoKey("Space", first > 0.5f || second > 0.5f, width = 150.dp)
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

/** A finger drawn across from [from] to [to] at height [y], with a fading trail behind it. */
@Composable
private fun Swipe(from: Dp, to: Dp, y: Dp, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Canvas(Modifier.width(LinesWidth).height(LineHeight * 3)) {
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
