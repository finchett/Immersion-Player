package io.github.immersionplayer.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import io.github.immersionplayer.App
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.dictionary.DictionaryInfo
import io.github.immersionplayer.dictionary.YomitanImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dictionaries by remember { mutableStateOf<List<DictionaryInfo>>(emptyList()) }
    var importing by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<DictionaryInfo?>(null) }
    var minedCount by remember { mutableIntStateOf(0) }

    suspend fun refresh() {
        dictionaries = withContext(Dispatchers.IO) { app.dictionaryDatabase.dictionaries() }
        minedCount = withContext(Dispatchers.IO) { app.miningStore.all().size }
    }
    val setupStatus by BundledDictionaries.status.collectAsState()
    LaunchedEffect(setupStatus) { refresh() }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "dictionary.zip"
        importing = "Importing $name…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    YomitanImporter(context, app.dictionaryDatabase).import(uri, name) { progress ->
                        importing = "Importing $name… ${progress.terms} terms, ${progress.meta} frequency/pitch entries"
                    }
                }
            }
            importing = null
            result.onFailure { importError = it.message ?: it.toString() }
            refresh()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Library") }
                Text("Dictionaries & settings", style = MaterialTheme.typography.headlineSmall)
            }

            Text("Dictionaries", style = MaterialTheme.typography.titleLarge)
            Text(
                "Import Yomitan dictionary zips — the same files Rikaitan/Yomitan use (Jitendex, JMdict, frequency lists, pitch accent). " +
                    "Earlier dictionaries are listed first in results.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dictionaries.isEmpty()) {
                Text("No dictionaries imported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            dictionaries.forEach { dict ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dict.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                if (dict.termCount > 0) append("${dict.termCount} terms")
                                if (dict.metaCount > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("${dict.metaCount} frequency/pitch entries")
                                }
                                if (dict.revision.isNotEmpty()) append(" · ${dict.revision}")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = dict.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                withContext(Dispatchers.IO) { app.dictionaryDatabase.setEnabled(dict.id, enabled) }
                                refresh()
                            }
                        },
                    )
                    TextButton(onClick = { confirmDelete = dict }) { Text("Remove") }
                }
            }
            setupStatus?.let {
                Text(it, color = MaterialTheme.colorScheme.tertiary)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val importStatus = importing
            if (importStatus != null) {
                Text(importStatus)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Button(onClick = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                    Text("Import dictionary (.zip)")
                }
            }

            HorizontalDivider()
            Text("Playback", style = MaterialTheme.typography.titleLarge)
            SettingSwitch(
                title = "Stop at the end of each line",
                description = "Pause when a line finishes, like mpvacious' play-up-to-next. Also toggled from the player.",
                initial = app.prefs.autoPause,
            ) { app.prefs.autoPause = it }
            SettingSwitch(
                title = "Pause while looking up words",
                description = "Tapping a word pauses the video.",
                initial = app.prefs.pauseOnLookup,
            ) { app.prefs.pauseOnLookup = it }
            SettingSwitch(
                title = "Copy each line to the clipboard",
                description = "For using an external dictionary app alongside the player. Android shows a small notice on each copy.",
                initial = app.prefs.copyLines,
            ) { app.prefs.copyLines = it }

            var size by remember { mutableStateOf(app.prefs.subtitleSize) }
            Text("Subtitle size: ${size.toInt()}")
            Slider(
                value = size,
                valueRange = 16f..44f,
                onValueChange = { size = it },
                onValueChangeFinished = { app.prefs.subtitleSize = size },
            )

            HorizontalDivider()
            Text("Saved words", style = MaterialTheme.typography.titleLarge)
            Text(
                "$minedCount saved. Each keeps the sentence, translation and timing for Anki export (coming later).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleLarge)
            Text(
                "Bundled dictionary: JMdict, © Electronic Dictionary Research and Development Group, " +
                    "used under the CC BY-SA 4.0 licence (edrdg.org/edrdg/licence.html). " +
                    "Yomitan conversion by rikaitan-import (Ajatt-Tools). " +
                    "Playback: libmpv via mpv-android.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    confirmDelete?.let { dict ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${dict.title}?") },
            text = { Text("You can import it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { app.dictionaryDatabase.delete(dict.id) }
                        refresh()
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}
