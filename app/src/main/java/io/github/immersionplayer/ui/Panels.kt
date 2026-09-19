package io.github.immersionplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.immersionplayer.dictionary.Definition
import io.github.immersionplayer.dictionary.TermEntry

private val HighlightBackground = Color(0xFF3B5BA5)
private val SelectionBackground = Color(0xFF7A5BA5)

/**
 * Text where tapping a character reports its index and dragging selects a range.
 * A long press reports hold start/end instead. With [autoSize], shrinks to fit its bounds.
 */
@Composable
fun TappableText(
    text: String,
    highlight: IntRange?,
    style: TextStyle,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    autoSize: TextAutoSize? = null,
    onHold: ((Boolean) -> Unit)? = null,
    onSelect: ((start: Int, end: Int) -> Unit)? = null,
    onTapOutside: (() -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selection by remember(text) { mutableStateOf<IntRange?>(null) }
    var holding by remember { mutableStateOf(false) }
    val shown = selection ?: highlight
    val selecting = selection != null
    val annotated = remember(text, shown, selecting) {
        buildAnnotatedString {
            append(text)
            if (shown != null && shown.first >= 0 && shown.last < text.length && !shown.isEmpty()) {
                val background = if (selecting) SelectionBackground else HighlightBackground
                addStyle(SpanStyle(background = background, color = Color.White), shown.first, shown.last + 1)
            }
        }
    }
    BasicText(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        autoSize = autoSize,
        modifier = modifier
            .pointerInput(text, onHold) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (holding) {
                            holding = false
                            onHold?.invoke(false)
                        }
                    },
                    onLongPress = onHold?.let { hold -> { holding = true; hold(true) } },
                    onTap = { position ->
                        val index = layout?.let { characterAt(it, position, text.length) }
                        if (index != null) onTap(index) else onTapOutside?.invoke()
                    },
                )
            }
            .pointerInput(text, onSelect) {
                if (onSelect == null) return@pointerInput
                var anchor = -1
                detectDragGestures(
                    onDragStart = { position ->
                        anchor = if (holding) -1 else layout?.let { characterAt(it, position, text.length) } ?: -1
                        if (anchor >= 0) selection = anchor..anchor
                    },
                    onDrag = { change, _ ->
                        val l = layout
                        if (anchor >= 0 && l != null) {
                            change.consume()
                            val index = l.getOffsetForPosition(change.position).coerceIn(0, text.length - 1)
                            selection = minOf(anchor, index)..maxOf(anchor, index)
                        }
                    },
                    onDragEnd = {
                        selection?.let { onSelect(it.first, it.last + 1) }
                        selection = null
                    },
                    onDragCancel = { selection = null },
                )
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
fun DictionaryPanel(
    lookup: ActiveLookup,
    hasDictionaries: Boolean,
    setupStatus: String?,
    onMine: (TermEntry) -> Unit,
    isMined: (TermEntry) -> Boolean,
) {
    val colors = GlossaryColors(
        tag = MaterialTheme.colorScheme.secondary,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        link = MaterialTheme.colorScheme.primary,
    )
    Column(Modifier.fillMaxSize()) {
        val result = lookup.result
        when {
            !hasDictionaries && setupStatus != null -> Message("$setupStatus\nThis only happens once.")
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
        // one heading per dictionary; some dictionaries (e.g. JMdict) store each sense as its own row
        entry.definitions.groupBy { it.dictionaryTitle }.forEach { (title, definitions) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // word-level tags (⭐, news, ichi…) repeat on every sense row, so show them once
                val wordTags = definitions.flatMap { it.termTags.split(' ') }.filter { it.isNotBlank() }.distinct()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Chip(title, MaterialTheme.colorScheme.primary)
                    wordTags.forEach { Chip(it, MaterialTheme.colorScheme.tertiary) }
                }
                val senses = definitions.filter { !it.isFormsList() }
                val forms = definitions.filter { it.isFormsList() }
                senses.forEachIndexed { index, definition ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (senses.size > 1) {
                            Text(
                                "${index + 1}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.width(18.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            val tags = definition.tagList()
                            if (tags.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    tags.forEach { Chip(it, MaterialTheme.colorScheme.secondary) }
                                }
                            }
                            val rendered = remember(definition.glossaryJson, colors) { Glossary.render(definition.glossaryJson, colors) }
                            Text(rendered, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                forms.forEach { definition ->
                    val rendered = remember(definition.glossaryJson, colors) { Glossary.render(definition.glossaryJson, colors) }
                    Text(
                        "Other forms: " + rendered.text.lines().joinToString("、") { it.substringAfter(". ").trim() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.padding(2.dp))
    }
}

/** Sense-level tags, without the sense numbers some dictionaries store as tags. */
private fun Definition.tagList(): List<String> =
    definitionTags.split(' ').filter { it.isNotBlank() && it.toIntOrNull() == null }.distinct()

/** rikaitan-import JMdict adds a separate "forms" row listing alternative spellings. */
private fun Definition.isFormsList(): Boolean = "forms" in definitionTags.split(' ')

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
