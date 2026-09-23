package io.github.immersionplayer.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.anki.AnkiConnect
import io.github.immersionplayer.anki.CardFormat
import io.github.immersionplayer.anki.CardFormats
import io.github.immersionplayer.anki.CardSource
import io.github.immersionplayer.dictionary.DictionaryInfo
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The panes of the settings screen, in the order they appear in its sidebar. */
enum class SettingsPane(val label: String) {
    General("General"),
    Dictionaries("Dictionaries"),
    Playback("Playback"),
    Appearance("Appearance"),
    Anki("Anki"),
    Keyboard("Keyboard"),
}

private val SidebarWidth = 216.dp
private val ContentWidth = 620.dp

/** Header content for the settings screen: the open pane's name, over the content it titles. */
@Composable
fun SettingsHeader(pane: SettingsPane) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(pane.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The sidebar: the way out of settings on the title-bar line (the traffic lights sit to its
 * left), the panes, and the version at the foot.
 */
@Composable
fun SettingsSidebar(pane: SettingsPane, onPane: (SettingsPane) -> Unit, onBack: () -> Unit) {
    Row(Modifier.width(SidebarWidth).fillMaxHeight()) {
        Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth().height(HeaderHeight).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) {
                TextAction("‹", onClick = onBack, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsPane.entries.forEach { entry ->
                    NavItem(entry.label, selected = entry == pane, onClick = { onPane(entry) })
                }
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.hairline)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // the app icon carries its own margin, so 40 dp draws a 32 dp tile
                Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(40.dp))
                Column {
                    Text("Immersion Player", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Version $APP_VERSION",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.hairline)
    }
}

/** The open pane: its settings as cards of rows, a section heading above each card. */
@Composable
fun SettingsBody(app: DesktopApp, pane: SettingsPane) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.widthIn(max = ContentWidth).padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            when (pane) {
                SettingsPane.General -> GeneralPane(app)
                SettingsPane.Dictionaries -> DictionariesPane(app)
                SettingsPane.Playback -> PlaybackPane(app)
                SettingsPane.Appearance -> AppearancePane(app)
                SettingsPane.Anki -> AnkiPane(app)
                SettingsPane.Keyboard -> KeyboardPane()
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) scheme.onSurface else scheme.onSurface.copy(alpha = 0.78f),
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> scheme.onSurface.copy(alpha = 0.10f)
                    hovered -> scheme.onSurface.copy(alpha = 0.05f)
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

// --- panes ----------------------------------------------------------------------------------

@Composable
private fun GeneralPane(app: DesktopApp) {
    val settings = app.settings
    Section("Library") {
        Card {
            SettingRow(
                title = "Media folder",
                detail = settings.libraryRoot ?: "No folder chosen",
                detailMono = settings.libraryRoot != null,
            ) {
                SmallButton(if (settings.libraryRoot == null) "Choose…" else "Change…") {
                    chooseFolder(settings.libraryRoot?.let(::File))?.let { settings.updateLibraryRoot(it.path) }
                }
            }
        }
    }
    Section("Languages") {
        Card {
            SettingRow("Studying") {
                LanguageSelect(settings.targetLanguage, allowNone = false) {
                    it?.let(settings::updateTargetLanguage)
                }
            }
            RowDivider()
            SettingRow("Peek language", detail = "Hold i to show subtitles in this language", detailKey = "i") {
                LanguageSelect(settings.peekLanguage, allowNone = true, onSelect = settings::updatePeekLanguage)
            }
        }
    }
}

@Composable
private fun DictionariesPane(app: DesktopApp) {
    val scope = rememberCoroutineScope()
    var dictionaries by remember { mutableStateOf<List<DictionaryInfo>>(emptyList()) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val setupStatus by app.setupStatus.collectAsState()
    LaunchedEffect(refreshCount, setupStatus) {
        dictionaries = withContext(Dispatchers.IO) { app.database.dictionaries() }
    }
    fun change(action: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { action() }
        refreshCount++
    }

    Section("Installed dictionaries", note = "Looked up in order, top first") {
        Card {
            setupStatus?.let { status ->
                SettingRow(status)
                RowDivider()
            }
            dictionaries.forEachIndexed { index, dictionary ->
                if (index > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Arrow("▲", enabled = index > 0) { change { app.database.move(dictionary.id, -1) } }
                        Arrow("▼", enabled = index < dictionaries.lastIndex) { change { app.database.move(dictionary.id, 1) } }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(dictionary.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${count(dictionary.termCount)} ${if (dictionary.termCount == 1) "term" else "terms"}" +
                                if (dictionary.metaCount > 0) " · ${count(dictionary.metaCount)} frequency/pitch" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Toggle(dictionary.enabled) { on -> change { app.database.setEnabled(dictionary.id, on) } }
                    TextAction(
                        "Remove",
                        onClick = { change { app.database.delete(dictionary.id) } },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // the footer strip that adds one
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextAction(
                    "+ Import Yomitan dictionary…",
                    enabled = importing == null,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    onClick = {
                        val file = chooseDictionaryZip() ?: return@TextAction
                        importError = null
                        importing = "Importing ${file.name}…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    YomitanImporter(app.database).import(file.name, { progress ->
                                        importing = "Importing ${file.name}… ${count(progress.terms)} terms"
                                    }) { file.inputStream() }
                                }
                            }
                            importing = null
                            result.onFailure {
                                app.logError("import ${file.name}", it)
                                importError = it.message ?: it.toString()
                            }
                            refreshCount++
                        }
                    },
                )
                importing?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        importError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp))
        }
    }
}

@Composable
private fun PlaybackPane(app: DesktopApp) {
    val settings = app.settings
    Section("Playback") {
        Card {
            ToggleRow(
                "Stop at the end of each line",
                "Pauses after every subtitle so you can read it",
                settings.autoPause, settings::updateAutoPause,
            )
            RowDivider()
            ToggleRow("Pause when you look up a word", null, settings.pauseOnLookup, settings::updatePauseOnLookup)
            RowDivider()
            ToggleRow("Copy each line to the clipboard", null, settings.copyLines, settings::updateCopyLines)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePane(app: DesktopApp) {
    val settings = app.settings
    Section("Theme") {
        Card {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Swatch("System", null, settings.theme == null, Modifier.weight(1f)) { settings.updateTheme(null) }
                AppTheme.entries.forEach { theme ->
                    Swatch(theme.label, theme, settings.theme == theme, Modifier.weight(1f)) { settings.updateTheme(theme) }
                }
            }
        }
    }
    Section("Video") {
        Card {
            ToggleRow(
                "Fill the video area",
                "Crops the edges instead of letterboxing",
                settings.videoFill, settings::updateVideoFill,
            )
            RowDivider()
            ToggleRow("Rounded corners", null, settings.roundedCorners, settings::updateRoundedCorners)
        }
    }
    Section("Subtitles") {
        Card {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Size", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                Text("A", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val scheme = MaterialTheme.colorScheme
                val colors = SliderDefaults.colors(
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
                Slider(
                    value = settings.subtitleSize,
                    onValueChange = settings::updateSubtitleSize,
                    valueRange = 18f..56f,
                    colors = colors,
                    // a plain bar and a round handle, not Material's tall pill and end dot
                    thumb = { Box(Modifier.size(15.dp).clip(CircleShape).background(scheme.primary)) },
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            colors = colors,
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                            modifier = Modifier.height(4.dp),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Text("A", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${settings.subtitleSize.toInt()} pt",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
            // the size as it will look over a video, rather than as a number
            Box(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().height(96.dp)
                    .clip(RoundedCornerShape(7.dp)).background(Color(0xFF16181C))
                    .border(1.dp, MaterialTheme.colorScheme.hairline, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    "今日はいい天気ですね",
                    color = Color.White,
                    fontSize = settings.subtitleSize.sp,
                    lineHeight = (settings.subtitleSize * 1.2f).sp,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }
    }
}

/** What AnkiConnect reported: the collection's note types and decks. */
private class AnkiCollection(val noteTypes: List<String>, val decks: List<String>)

@Composable
private fun AnkiPane(app: DesktopApp) {
    val settings = app.settings
    var refresh by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf<Result<AnkiCollection>?>(null) }
    LaunchedEffect(settings.ankiEnabled, settings.ankiUrl, refresh) {
        if (!settings.ankiEnabled) return@LaunchedEffect
        collection = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val anki = AnkiConnect(settings.ankiUrl)
                anki.version()
                AnkiCollection(anki.modelNames(), anki.deckNames())
            }
        }
        collection = result
        // first time: the note type of a format known field by field, if the collection has one
        val noteTypes = result.getOrNull()?.noteTypes.orEmpty()
        if (settings.ankiNoteType == null) {
            noteTypes.firstOrNull { name -> CardFormats.presets.any { it.noteType.equals(name, ignoreCase = true) } }
                ?.let(settings::updateAnkiNoteType)
        }
    }

    // the chosen note type's fields, in Anki's order, with a mapping for every one of them
    var fields by remember { mutableStateOf<List<String>>(emptyList()) }
    val connected = collection?.getOrNull()
    LaunchedEffect(connected, settings.ankiNoteType) {
        fields = emptyList()
        val noteType = settings.ankiNoteType ?: return@LaunchedEffect
        if (connected == null) return@LaunchedEffect
        val names = withContext(Dispatchers.IO) {
            runCatching { AnkiConnect(settings.ankiUrl).modelFieldNames(noteType) }.getOrNull()
        } ?: return@LaunchedEffect
        val saved = settings.cardFormat(noteType)
        if (saved == null || saved.fields.keys != names.toSet()) {
            val suggested = CardFormats.forNoteType(noteType, names).fields
            settings.saveCardFormat(CardFormat(noteType, names.associateWith { saved?.fields?.get(it) ?: suggested.getValue(it) }))
        }
        fields = names
    }

    // the address is saved once typing pauses, so each keystroke isn't a connection attempt
    var typed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typed) {
        val url = typed?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        delay(600)
        settings.updateAnkiUrl(url)
    }

    Section("Anki cards") {
        Card {
            ToggleRow(
                "Make Anki cards",
                "Press A in the player, or + Anki on a word",
                settings.ankiEnabled, settings::updateAnkiEnabled,
            )
            if (settings.ankiEnabled) {
                RowDivider()
                SettingRow(
                    "AnkiConnect",
                    detail = when {
                        collection == null -> "Connecting…"
                        connected != null -> "Connected"
                        else -> collection?.exceptionOrNull()?.message ?: "Couldn't connect"
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(typed ?: settings.ankiUrl, { typed = it }, width = 190.dp, placeholder = AnkiConnect.DEFAULT_URL)
                        SmallButton("Retry") { refresh++ }
                    }
                }
            }
        }
        if (settings.ankiEnabled && collection?.isFailure == true) {
            Text(
                "Cards are added through Anki, so it has to be open, with the AnkiConnect add-on " +
                    "(code 2055492159, under Tools › Add-ons › Get Add-ons).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            )
        }
    }
    if (!settings.ankiEnabled || connected == null) return

    Section("Cards") {
        Card {
            SettingRow("Note type") {
                Select(
                    settings.ankiNoteType ?: "Choose…", connected.noteTypes,
                    onSelect = { settings.updateAnkiNoteType(connected.noteTypes[it]) },
                    width = 220.dp, muted = settings.ankiNoteType == null,
                )
            }
            RowDivider()
            SettingRow("Deck") {
                Select(
                    settings.ankiDeck ?: "Choose…", connected.decks,
                    onSelect = { settings.updateAnkiDeck(connected.decks[it]) },
                    width = 220.dp, muted = settings.ankiDeck == null,
                )
            }
            RowDivider()
            SettingRow("Tags", "Separated by spaces") {
                TextInput(settings.ankiTags, settings::updateAnkiTags, width = 220.dp)
            }
        }
    }

    val noteType = settings.ankiNoteType
    val format = settings.ankiFormat
    if (noteType != null && format != null && fields.isNotEmpty()) {
        val preset = CardFormats.presets.any { it.noteType.equals(noteType, ignoreCase = true) }
        Section("Fields", note = if (preset) "A known format: set up already" else "Guessed from the field names") {
            Card {
                fields.forEachIndexed { index, field ->
                    if (index > 0) RowDivider()
                    val source = format.fields[field] ?: CardSource.None
                    SettingRow(field) {
                        Select(
                            source.label, CardSource.entries.map { it.label },
                            onSelect = { settings.saveCardFormat(format.copy(fields = format.fields + (field to CardSource.entries[it]))) },
                            width = 240.dp, muted = source == CardSource.None,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    TextAction(
                        if (preset) "Reset to the $noteType defaults" else "Reset to the suggested fields",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        onClick = { settings.saveCardFormat(CardFormats.forNoteType(noteType, fields)) },
                    )
                }
            }
        }
    }

    Section("Media") {
        Card {
            SettingRow("Audio around the line", "Kept before and after it, so the first and last sounds aren't cut") {
                val paddings = listOf(0.0, 0.15, 0.3, 0.5, 0.75)
                Select(
                    seconds(settings.ankiAudioPadding), paddings.map(::seconds),
                    onSelect = { settings.updateAnkiAudioPadding(paddings[it]) },
                    width = 100.dp,
                )
            }
            RowDivider()
            SettingRow(
                "Screenshot",
                if (settings.ankiImageAnimated) "The whole line, moving, like a GIF" else "The frame on screen when you add the card",
            ) {
                val kinds = listOf("Animated", "Still")
                Select(
                    kinds[if (settings.ankiImageAnimated) 0 else 1], kinds,
                    onSelect = { settings.updateAnkiImageAnimated(it == 0) },
                    width = 100.dp,
                )
            }
            RowDivider()
            SettingRow(
                "Screenshot height",
                if (settings.ankiImageAnimated) "AVIF: 15–60 KB a second at 360 px, by motion" else "AVIF: about 10 KB at 360 px",
            ) {
                val heights = listOf(240, 360, 480, 720)
                Select(
                    "${settings.ankiImageHeight} px", heights.map { "$it px" },
                    onSelect = { settings.updateAnkiImageHeight(heights[it]) },
                    width = 100.dp,
                )
            }
        }
    }
}

private fun seconds(value: Double) = "%s s".format(value.toString().removeSuffix(".0"))

@Composable
private fun KeyboardPane() {
    Section("In the player") {
        Card {
            KEYS.forEachIndexed { index, key ->
                if (index > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(key.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    key.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    key.caps.forEach { Keycap(it) }
                }
            }
        }
    }
}

/** One row of the keyboard pane: the keys to press, and what they do. */
class KeyHelp(val caps: List<String>, val action: String, val note: String? = null)

val KEYS = listOf(
    KeyHelp(listOf("Space"), "Play / pause"),
    KeyHelp(listOf("H", "L"), "Previous / next line, then keep playing"),
    KeyHelp(listOf("J", "K"), "Previous / next line, stop at its end"),
    KeyHelp(listOf("L", "K"), "Carry on playing; press again to jump to the next line", "while paused"),
    KeyHelp(listOf(";"), "Replay this line, stop at its end"),
    KeyHelp(listOf("I"), "Show the peek language", "hold, or hold the line"),
    KeyHelp(listOf("Y", "O"), "Seek 5 s back / forward"),
    KeyHelp(listOf("N", "M"), "Shift subtitles 0.1 s earlier / later"),
    KeyHelp(listOf("U"), "Stop at the end of every line, on or off"),
    KeyHelp(listOf("F"), "Full screen", "or double-click"),
    KeyHelp(listOf("Z"), "Fill the area, or fit the whole picture", "or ⌘ + scroll"),
    KeyHelp(listOf("Esc"), "Leave full screen, then back to the library"),
    KeyHelp(listOf("A"), "Add the looked-up word to Anki", "when Anki cards are on"),
    KeyHelp(emptyList(), "Look up a word", "click or drag it"),
)

// --- pieces ---------------------------------------------------------------------------------

/** A card sits barely off the page: a touch lighter on a light theme, lifted on a dark one. */
private val ColorScheme.card: Color
    get() = Color.White.copy(alpha = if (background.luminance() > 0.5f) 0.6f else 0.05f).compositeOver(background)

/** The background of a control that takes input, a step further again. */
private val ColorScheme.control: Color
    get() = if (background.luminance() > 0.5f) Color.White else Color.White.copy(alpha = 0.09f).compositeOver(card)

/** The line around a card or between its rows: present, but only just. */
val ColorScheme.hairline: Color get() = onSurface.copy(alpha = 0.11f)
private val ColorScheme.rowLine: Color get() = onSurface.copy(alpha = 0.07f)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
    )
}

private fun count(n: Int) = "%,d".format(n)

@Composable
private fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            note?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
        content()
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.card)
            .border(1.dp, MaterialTheme.colorScheme.hairline, RoundedCornerShape(10.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.rowLine)

/**
 * A row of one card: what the setting is, an explanation under it, and the control that changes
 * it. [detailKey] is drawn as a keycap where it appears in [detail].
 */
@Composable
private fun SettingRow(
    title: String,
    detail: String? = null,
    detailMono: Boolean = false,
    detailKey: String? = null,
    control: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    val before = detailKey?.let { detail.substringBefore(" $it ") }
                    if (before != null) {
                        Text("$before ", style = MaterialTheme.typography.bodySmall, color = muted)
                        Keycap(detailKey.uppercase())
                        Text(" ${detail.substringAfter(" $detailKey ")}", style = MaterialTheme.typography.bodySmall, color = muted)
                    } else {
                        Text(
                            detail,
                            style = if (detailMono) MaterialTheme.typography.bodySmall.copy(fontFamily = Mono)
                            else MaterialTheme.typography.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
            }
        }
        control()
    }
}

@Composable
private fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.clickable { onChange(!checked) }) {
        SettingRow(title, detail) { Toggle(checked, onChange) }
    }
}

/** The small switch of the design, rather than Material's finger-sized one. */
@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val knob by animateDpAsState(if (checked) 16.dp else 2.dp, label = "knob")
    val track by animateColorAsState(
        if (checked) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.3f),
        label = "track",
    )
    Box(
        Modifier.size(32.dp, 18.dp).clip(CircleShape).background(track)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = knob).size(14.dp).clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) scheme.onSurface.copy(alpha = 0.06f).compositeOver(scheme.control) else scheme.control)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** The language pickers. */
@Composable
private fun LanguageSelect(selected: String?, allowNone: Boolean, onSelect: (String?) -> Unit) {
    val options = (if (allowNone) listOf<String?>(null) else emptyList()) + SubtitleFiles.LANGUAGES.map { it.code }
    Select(
        text = selected?.let { SubtitleFiles.language(it)?.name ?: it } ?: "Off",
        options = options.map { code -> code?.let { SubtitleFiles.language(it)?.name ?: it } ?: "Off" },
        onSelect = { onSelect(options[it]) },
    )
}

/** A control that looks like a select, with the menu of [options] under it. */
@Composable
private fun Select(text: String, options: List<String>, onSelect: (Int) -> Unit, width: Dp = 150.dp, muted: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier.width(width)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.control)
                .border(1.dp, scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .clickable { open = true }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (muted) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(index); open = false })
            }
        }
    }
}

/** A one-line text box in the style of [Select]; [onChange] runs on every edit. */
@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, width: Dp = 220.dp, placeholder: String = "") {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(color = scheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(scheme.primary),
        modifier = Modifier.width(width),
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.control)
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = style, color = scheme.onSurfaceVariant.copy(alpha = 0.6f))
                field()
            }
        },
    )
}

/** One of the dictionary reorder arrows. */
@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    TextAction(
        glyph,
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.width(20.dp),
    )
}

/** A theme as a tile of its own colours; [theme] null is the one that follows the system. */
@Composable
private fun Swatch(label: String, theme: AppTheme?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill = theme?.scheme?.background
    val sample = theme?.scheme?.onBackground ?: Color(0xFF8A8070)
    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(11.dp))
                .padding(2.dp)
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (fill != null) Brush.linearGradient(0f to fill, 1f to fill)
                    // the system tile is split: the light theme on one side, the dark on the other
                    else Brush.horizontalGradient(
                        0f to AppTheme.Paper.scheme.background, 0.5f to AppTheme.Paper.scheme.background,
                        0.5f to AppTheme.Midnight.scheme.background, 1f to AppTheme.Midnight.scheme.background,
                    ),
                )
                .border(1.dp, scheme.hairline, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "あ Aa",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = sample,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Keycap(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(scheme.control)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(5.dp))
            .widthIn(min = 22.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        textAlign = TextAlign.Center,
    )
}

private val Mono = androidx.compose.ui.text.font.FontFamily.Monospace
