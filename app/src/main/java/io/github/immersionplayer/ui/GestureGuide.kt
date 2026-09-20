package io.github.immersionplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Gesture(val symbol: String, val action: String, val detail: String)

private val videoGestures = listOf(
    Gesture("◉", "Tap", "play or pause"),
    Gesture("◉◉", "Double-tap left or right edge", "jump 5s back or forward; keep tapping to stack"),
    Gesture("↔", "Drag sideways", "scrub; move up or down while dragging to go faster"),
    Gesture("⤢", "Pinch", "fill the area, or fit the whole picture"),
    Gesture("▁", "Bottom line (paused)", "tap or drag it to seek"),
)

private val lineGestures = listOf(
    Gesture("◉", "Tap a word", "look it up"),
    Gesture("↔", "Drag across the text", "look up exactly what you select"),
    Gesture("↑", "Swipe up", "replay this line"),
    Gesture("⊙", "Hold", "show the English translation"),
)

private val panelGestures = listOf(
    Gesture("↔", "Swipe sideways", "previous or next line"),
    Gesture("◉", "Tap", "play or pause"),
    Gesture("◉◉", "Double-tap", "stop at the end of each line, on or off"),
    Gesture("⇔", "Drag the divider", "resize the video and the panel"),
)

/** First-run guide to the gestures; also reachable from settings. */
@Composable
fun GestureGuide(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints {
            val twoColumns = maxWidth > 640.dp
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 820.dp).padding(20.dp).safeDrawingPadding(),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("How it works", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "There are no buttons: everything is a gesture.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                GestureGroup("The video", videoGestures)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                GestureGroup("The Japanese line", lineGestures)
                                GestureGroup("The panel", panelGestures)
                            }
                        }
                    } else {
                        GestureGroup("The video", videoGestures)
                        GestureGroup("The Japanese line", lineGestures)
                        GestureGroup("The panel", panelGestures)
                    }

                    Text(
                        "Every line has a word looked up already — the first kanji it can find.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Start watching") }
                }
            }
        }
    }
}

@Composable
private fun GestureGroup(title: String, gestures: List<Gesture>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        gestures.forEach { gesture ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    gesture.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(44.dp),
                )
                Column {
                    Text(gesture.action, style = MaterialTheme.typography.titleSmall)
                    Text(
                        gesture.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
