package io.github.immersionplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.App
import io.github.immersionplayer.anki.AnkiDroid
import io.github.immersionplayer.anki.CardFormat
import io.github.immersionplayer.anki.CardFormats
import io.github.immersionplayer.anki.CardSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What AnkiDroid reported: the collection's note types and decks. */
private class AnkiCollection(val noteTypes: List<String>, val decks: List<String>)

/** The Anki section of the settings: cards go to AnkiDroid on the phone. */
@Composable
fun AnkiSettings(app: App) {
    val context = LocalContext.current
    val prefs = app.prefs
    var enabled by remember { mutableStateOf(prefs.ankiEnabled) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    SettingRow(
        "Make Anki cards",
        "Tap + on a word for a card with its line, audio and a screenshot. Cards go straight into " +
            "AnkiDroid on this phone; it doesn't need to be open.",
    ) {
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.ankiEnabled = it
            if (it && AnkiDroid.installed(context) && !AnkiDroid.permitted(context)) permission.launch(AnkiDroid.PERMISSION)
        })
    }
    if (!enabled) return

    var collection by remember { mutableStateOf<Result<AnkiCollection>?>(null) }
    var noteType by remember { mutableStateOf(prefs.ankiNoteType) }
    var deck by remember { mutableStateOf(prefs.ankiDeck) }
    LaunchedEffect(refresh) {
        collection = withContext(Dispatchers.IO) {
            runCatching {
                val anki = AnkiDroid(context)
                AnkiCollection(anki.noteTypes(), anki.decks())
            }
        }
        // first time: the note type of a format known field by field, if the collection has one
        val noteTypes = collection?.getOrNull()?.noteTypes.orEmpty()
        if (noteType == null) {
            CardFormats.pick(noteTypes)?.let { noteType = it; prefs.ankiNoteType = it }
        }
    }

    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    val connected = collection?.getOrNull()
    if (connected == null) {
        val problem = AnkiDroid.unavailable(context) ?: collection?.exceptionOrNull()?.message
        SettingRow(
            "AnkiDroid",
            // a fresh AnkiDroid has no collection until it has been opened once
            problem ?: if (collection == null) "Connecting…" else "Couldn't reach AnkiDroid. Open it once, then retry.",
        ) {
            when {
                !AnkiDroid.installed(context) -> Unit
                !AnkiDroid.permitted(context) -> OutlinedButton(onClick = { permission.launch(AnkiDroid.PERMISSION) }) { Text("Allow") }
                else -> TextButton(onClick = { refresh++ }) { Text("Retry") }
            }
        }
        return
    }

    ChoiceRow("Note type", noteType, connected.noteTypes) { noteType = it; prefs.ankiNoteType = it }
    NoteTypeSource(
        if (CardFormats.pick(connected.noteTypes) != null) "Cards are set up for Japanese sentences, the mpvacious note type."
        else "Cards are set up for Japanese sentences, the mpvacious note type, which isn't in AnkiDroid yet.",
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    ChoiceRow("Deck", deck, connected.decks) { deck = it; prefs.ankiDeck = it }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    var tags by remember { mutableStateOf(prefs.ankiTags) }
    SettingRow("Tags", "Separated by spaces.") {
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it; prefs.ankiTags = it },
            singleLine = true,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }

    // the chosen note type's fields, in AnkiDroid's order, with a source for every one of them
    var format by remember { mutableStateOf<CardFormat?>(null) }
    var fields by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(noteType) {
        val name = noteType ?: return@LaunchedEffect
        val names = withContext(Dispatchers.IO) { runCatching { AnkiDroid(context).fieldNames(name) }.getOrNull() }
            ?: return@LaunchedEffect
        val saved = prefs.cardFormat(name)
        val current = if (saved == null || saved.fields.keys != names.toSet()) {
            val suggested = CardFormats.forNoteType(name, names).fields
            CardFormat(name, names.associateWith { saved?.fields?.get(it) ?: suggested.getValue(it) }).also(prefs::saveCardFormat)
        } else saved
        fields = names
        format = current
    }
    val shownFormat = format?.takeIf { it.noteType == noteType }
    if (shownFormat != null) {
        fields.forEach { field ->
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val source = shownFormat.fields[field] ?: CardSource.None
            ChoiceRow(field, source.label, CardSource.entries.map { it.label }) { label ->
                val chosen = CardSource.entries.first { it.label == label }
                val updated = shownFormat.copy(fields = shownFormat.fields + (field to chosen))
                prefs.saveCardFormat(updated)
                format = updated
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        TextButton(
            onClick = { CardFormats.forNoteType(shownFormat.noteType, fields).also(prefs::saveCardFormat).also { format = it } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Reset the fields") }
    }

    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    val paddings = listOf(0f, 0.15f, 0.3f, 0.5f, 0.75f)
    var padding by remember { mutableStateOf(prefs.ankiAudioPadding) }
    ChoiceRow("Audio around the line", seconds(padding), paddings.map(::seconds), "So the first and last sounds aren't cut.") { label ->
        padding = paddings.first { seconds(it) == label }
        prefs.ankiAudioPadding = padding
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    var animated by remember { mutableStateOf(prefs.ankiImageAnimated) }
    val kinds = listOf("Animated", "Still")
    ChoiceRow(
        "Screenshot", kinds[if (animated) 0 else 1], kinds,
        if (animated) "The whole line, moving, like a GIF." else "The frame on screen when you add the card.",
    ) { animated = it == kinds[0]; prefs.ankiImageAnimated = animated }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    val heights = listOf(240, 360, 480, 720)
    var height by remember { mutableIntStateOf(prefs.ankiImageHeight) }
    ChoiceRow(
        "Screenshot height", "$height px", heights.map { "$it px" },
        if (animated) "AVIF: roughly 25 KB a second at 360 px." else "WebP.",
    ) { label ->
        height = heights.first { "$it px" == label }
        prefs.ankiImageHeight = height
    }
}

private fun seconds(value: Float) = value.toString().removeSuffix(".0") + " s"

/** A setting chosen from a menu: the current value on a button that opens it. */
@Composable
private fun ChoiceRow(
    title: String,
    current: String?,
    options: List<String>,
    description: String? = null,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, description) {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.widthIn(max = 220.dp)) {
                Text(
                    (current ?: "Choose…") + "  ▾",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (current == null || current == CardSource.None.label) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
                }
            }
        }
    }
}
