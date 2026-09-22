package io.github.immersionplayer.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.dictionary.DictionaryInfo
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.subs.SubtitleFiles
import io.github.immersionplayer.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: DesktopApp, onBack: () -> Unit) {
    val settings = app.settings
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = TitleBarInset + 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction("‹", onClick = onBack, style = MaterialTheme.typography.titleLarge)
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.widthIn(max = 720.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Section("Languages") {
                    LanguageRow(
                        label = "Studying",
                        selected = settings.targetLanguage,
                        allowNone = false,
                        onSelect = { it?.let(settings::updateTargetLanguage) },
                    )
                    LanguageRow(
                        label = "Peek (hold i)",
                        selected = settings.peekLanguage,
                        allowNone = true,
                        onSelect = settings::updatePeekLanguage,
                    )
                }

                Section("Dictionaries") {
                    setupStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    dictionaries.forEachIndexed { index, dictionary ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dictionary.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${dictionary.termCount} terms" +
                                        (if (dictionary.metaCount > 0) " · ${dictionary.metaCount} frequency/pitch" else ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextAction("↑", onClick = { change { app.database.move(dictionary.id, -1) } }, enabled = index > 0)
                            TextAction(
                                "↓",
                                onClick = { change { app.database.move(dictionary.id, 1) } },
                                enabled = index < dictionaries.lastIndex,
                            )
                            Switch(dictionary.enabled, onCheckedChange = { on -> change { app.database.setEnabled(dictionary.id, on) } })
                            TextAction("Remove", onClick = { change { app.database.delete(dictionary.id) } })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = importing == null,
                            onClick = {
                                val file = chooseDictionaryZip() ?: return@OutlinedButton
                                importError = null
                                importing = "Importing ${file.name}…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            YomitanImporter(app.database).import(file.name, { progress ->
                                                importing = "Importing ${file.name}… ${progress.terms} terms"
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
                        ) { Text("Import Yomitan dictionary…") }
                        importing?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                Section("Playback") {
                    Toggle("Stop at the end of each line", settings.autoPause, settings::updateAutoPause)
                    Toggle("Pause when you look up a word", settings.pauseOnLookup, settings::updatePauseOnLookup)
                    Toggle("Copy each line to the clipboard", settings.copyLines, settings::updateCopyLines)
                }

                Section("Appearance") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextAction(
                            "System",
                            onClick = { settings.updateTheme(null) },
                            color = if (settings.theme == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppTheme.entries.forEach { theme ->
                            TextAction(
                                theme.label,
                                onClick = { settings.updateTheme(theme) },
                                color = if (settings.theme == theme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("Subtitle size: ${settings.subtitleSize.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.subtitleSize,
                        onValueChange = { settings.updateSubtitleSize(it) },
                        valueRange = 18f..56f,
                    )
                }

                Section("Keys") {
                    KEYS.forEach { (key, action) ->
                        Row {
                            Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(min = 160.dp))
                            Text(action, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

val KEYS = listOf(
    "Space" to "play / pause",
    "j / k" to "previous / next line",
    "h / l" to "previous / next line, stop at its end",
    ";" to "replay this line, stop at its end",
    "hold i, or hold the line" to "show the peek language",
    "y / o" to "seek 5 s back / forward",
    "n / m" to "shift subtitles 0.1 s earlier / later",
    "u" to "stop at end of every line on/off",
    "f or double-click" to "full screen",
    "Esc" to "leave full screen, then back to the library",
    "click / drag a word" to "look it up",
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun LanguageRow(label: String, selected: String?, allowNone: Boolean, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextAction(
                (selected?.let { SubtitleFiles.language(it)?.name ?: it } ?: "None") + " ▾",
                onClick = { open = true },
                color = MaterialTheme.colorScheme.primary,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); open = false })
                SubtitleFiles.LANGUAGES.forEach { language ->
                    DropdownMenuItem(text = { Text(language.name) }, onClick = { onSelect(language.code); open = false })
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}
