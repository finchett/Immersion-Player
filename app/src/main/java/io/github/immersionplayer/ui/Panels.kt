package io.github.immersionplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.dictionary.TermEntry
import io.github.immersionplayer.subs.SubtitleTrack

private val HighlightBackground = Color(0xFF3B5BA5)

/** Text where tapping a character reports its index. */
@Composable
fun TappableText(
    text: String,
    highlight: IntRange?,
    style: TextStyle,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(text, highlight) {
        buildAnnotatedString {
            append(text)
            if (highlight != null && highlight.first >= 0 && highlight.last < text.length && !highlight.isEmpty()) {
                addStyle(SpanStyle(background = HighlightBackground, color = Color.White), highlight.first, highlight.last + 1)
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { position ->
                val l = layout ?: return@detectTapGestures
                characterAt(l, position, text.length)?.let(onTap)
            }
        },
    )
}

private fun characterAt(layout: TextLayoutResult, position: Offset, length: Int): Int? {
    if (length == 0) return null
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.x < layout.getLineLeft(line) - 8 || position.x > layout.getLineRight(line) + 8) return null
    var offset = layout.getOffsetForPosition(position).coerceIn(0, length - 1)
    // getOffsetForPosition returns the nearest caret; step back if the tap is on the previous character
    if (offset > 0 && position.x < layout.getBoundingBox(offset).left) offset--
    if (offset < length - 1 && position.x > layout.getBoundingBox(offset).right) offset++
    return offset
}

@Composable
fun TranscriptPanel(
    track: SubtitleTrack?,
    lineIndex: Int,
    status: String?,
    onPlayLine: (Int) -> Unit,
    onCharTap: (Int, String, Int) -> Unit,
) {
    if (track == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            if (status == "Reading subtitles…") CircularProgressIndicator()
            else Text(status ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(lineIndex) {
        if (lineIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((lineIndex - 2).coerceAtLeast(0))
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(track.cues) { index, cue ->
            val current = index == lineIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (current) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    formatTime(cue.start),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(52.dp).clickable { onPlayLine(index) }.padding(top = 4.dp),
                )
                TappableText(
                    text = cue.text,
                    highlight = null,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        color = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    onTap = { onCharTap(index, cue.text, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun DictionaryPanel(
    lookup: ActiveLookup,
    hasDictionaries: Boolean,
    onClose: () -> Unit,
    onMine: (TermEntry) -> Unit,
    isMined: (TermEntry) -> Boolean,
) {
    val colors = GlossaryColors(
        tag = MaterialTheme.colorScheme.secondary,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        link = MaterialTheme.colorScheme.primary,
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val matched = lookup.result?.matchLength ?: 0
            Text(
                buildAnnotatedString {
                    val end = (lookup.start + maxOf(matched, 1)).coerceAtMost(lookup.text.length)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(lookup.text.substring(lookup.start, end))
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(lookup.text.substring(end).take(12))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = onClose) { Text("Transcript ✕") }
        }
        HorizontalDivider()

        val result = lookup.result
        when {
            !hasDictionaries -> Message("No dictionaries yet. Import a Yomitan dictionary (e.g. Jitendex) under Dictionaries & settings.")
            result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            result.entries.isEmpty() -> Message("No match.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(result.entries) { _, entry ->
                    TermCard(entry, colors, onMine, isMined)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TermCard(
    entry: TermEntry,
    colors: GlossaryColors,
    onMine: (TermEntry) -> Unit,
    isMined: (TermEntry) -> Boolean,
) {
    var mined by remember(entry) { mutableStateOf(isMined(entry)) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (entry.reading != entry.expression) {
                    Text(entry.reading, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
                Text(entry.expression, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            OutlinedButton(onClick = { onMine(entry); mined = true }, enabled = !mined) {
                Text(if (mined) "✓ Saved" else "+ Save")
            }
        }
        if (entry.reasons.isNotEmpty() || entry.frequencies.isNotEmpty() || entry.pitchPositions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.reasons.isNotEmpty()) {
                    Text(
                        "« " + entry.reasons.joinToString(" « "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                entry.frequencies.forEach { Chip("${it.dictionaryTitle} ${it.display}", MaterialTheme.colorScheme.tertiary) }
                if (entry.pitchPositions.isNotEmpty()) {
                    Chip("pitch " + entry.pitchPositions.joinToString(" ") { "[$it]" }, MaterialTheme.colorScheme.primary)
                }
            }
        }
        entry.definitions.forEach { definition ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(definition.dictionaryTitle, MaterialTheme.colorScheme.primary)
                    (definition.definitionTags.split(' ') + definition.termTags.split(' '))
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(4)
                        .forEach { Chip(it, MaterialTheme.colorScheme.secondary) }
                }
                val rendered = remember(definition.glossaryJson, colors) { Glossary.render(definition.glossaryJson, colors) }
                Text(
                    rendered,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.padding(2.dp))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
