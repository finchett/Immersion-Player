package io.github.immersionplayer.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.App
import io.github.immersionplayer.dictionary.BundledDictionaries
import io.github.immersionplayer.dictionary.DictionaryInfo
import io.github.immersionplayer.dictionary.YomitanImporter
import io.github.immersionplayer.triggers.ShoulderTriggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var showAdvanced by remember { mutableStateOf(false) }

    suspend fun refresh() {
        dictionaries = withContext(Dispatchers.IO) { app.dictionaryDatabase.dictionaries() }
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
                        importing = "Importing $name… ${progress.terms} terms"
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
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.clickable(onClick = onBack),
                    ) {
                        Text(
                            "‹  Library",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }

                SettingsSection("Dictionaries") {
                    if (dictionaries.isEmpty()) {
                        SettingRow("No dictionaries yet", "Import a Yomitan zip below.")
                    }
                    dictionaries.forEachIndexed { index, dict ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        DictionaryRow(
                            dict = dict,
                            isFirst = index == 0,
                            isLast = index == dictionaries.lastIndex,
                            onMove = { direction ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { app.dictionaryDatabase.move(dict.id, direction) }
                                    refresh()
                                }
                            },
                            onEnabled = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { app.dictionaryDatabase.setEnabled(dict.id, enabled) }
                                    refresh()
                                }
                            },
                            onRemove = { confirmDelete = dict },
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Yomitan and Rikaitan dictionaries work here: Jitendex, JMdict, frequency lists, " +
                                "pitch accent. The ones higher up appear first in results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val status = setupStatus ?: importing
                        if (status != null) {
                            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            Button(onClick = {
                                pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            }) { Text("Import dictionary (.zip)") }
                        }
                    }
                }

                SettingsSection("Playback") {
                    SettingSwitchRow(
                        title = "Stop at the end of each line",
                        description = "Pause when a line finishes. Double-tap the study panel to toggle it while watching.",
                        initial = app.prefs.autoPause,
                    ) { app.prefs.autoPause = it }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingSwitchRow(
                        title = "Pause while looking up words",
                        description = "Tapping a word pauses the video.",
                        initial = app.prefs.pauseOnLookup,
                    ) { app.prefs.pauseOnLookup = it }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingSwitchRow(
                        title = "Copy each line to the clipboard",
                        description = "For using another dictionary app alongside the player.",
                        initial = app.prefs.copyLines,
                    ) { app.prefs.copyLines = it }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    var size by remember { mutableStateOf(app.prefs.subtitleSize) }
                    SettingRow("Subtitle size", "${size.toInt()} pt")
                    Slider(
                        value = size,
                        valueRange = 16f..44f,
                        onValueChange = { size = it },
                        onValueChangeFinished = { app.prefs.subtitleSize = size },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                SettingsSection("Appearance") {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppTheme.entries.forEach { theme ->
                                FilterChip(
                                    selected = app.appearance.theme == theme,
                                    onClick = { app.appearance.updateTheme(theme) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .background(theme.scheme.background, CircleShape)
                                                .border(3.dp, theme.scheme.primary, CircleShape),
                                        )
                                    },
                                    label = { Text(theme.label) },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingSwitchRow(
                        title = "Rounded corners",
                        description = "Show the video and study panel as rounded cards.",
                        initial = app.appearance.roundedCorners,
                    ) { app.appearance.updateRoundedCorners(it) }
                }

                SettingsSection(
                    title = "Advanced",
                    trailing = {
                        TextButton(onClick = { showAdvanced = !showAdvanced }) {
                            Text(if (showAdvanced) "Hide" else "Show")
                        }
                    },
                ) {
                    if (!showAdvanced) {
                        SettingRow("Shoulder triggers", "For RedMagic phones, via Shizuku.")
                    }
                    AnimatedVisibility(showAdvanced) {
                        Column { ShoulderTriggerSettings(app) }
                    }
                }

                Text(
                    "Bundled dictionary: JMdict, © Electronic Dictionary Research and Development Group, used " +
                        "under CC BY-SA 4.0 (edrdg.org/edrdg/licence.html). Yomitan conversion by rikaitan-import. " +
                        "Playback: libmpv via mpv-android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
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
private fun DictionaryRow(
    dict: DictionaryInfo,
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 10.dp)) {
            CompactButton("↑", enabled = !isFirst) { onMove(-1) }
            Spacer(Modifier.height(3.dp))
            CompactButton("↓", enabled = !isLast) { onMove(1) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dict.title, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    if (dict.termCount > 0) append("${"%,d".format(dict.termCount)} terms")
                    if (dict.metaCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${"%,d".format(dict.metaCount)} frequency/pitch")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = dict.enabled, onCheckedChange = onEnabled)
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun CompactButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(width = 30.dp, height = 22.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shoulder triggers need Shizuku, so this shows what's still missing and can test them live. */
@Composable
private fun ShoulderTriggerSettings(app: App) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(app.prefs.shoulderTriggers) }
    var status by remember { mutableStateOf(ShoulderTriggers.status(context)) }
    var testing by remember { mutableStateOf(false) }
    val lastPress by ShoulderTriggers.lastPress.collectAsState()
    val running by ShoulderTriggers.running.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            status = ShoulderTriggers.status(context)
            delay(1000)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (testing) {
                ShoulderTriggers.testMode = false
                ShoulderTriggers.stop()
            }
        }
    }

    Column {
        SettingRow(
            title = "Shoulder triggers change lines",
            description = "RedMagic's triggers: left for the previous line, right for the next. The phone only " +
                "powers them in Game Space, so the app switches them on itself through Shizuku.",
        ) {
            Switch(checked = enabled, onCheckedChange = { enabled = it; app.prefs.shoulderTriggers = it })
        }
        if (!enabled) return@Column

        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (status) {
                ShoulderTriggers.Status.NotInstalled -> {
                    Text("Shizuku isn't installed.", color = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Shizuku is a free app that grants adb-level access. Install it, open it and follow its " +
                            "wireless-debugging steps, then come back. Everything else works without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ShoulderTriggers.Status.NotRunning -> {
                    Text("Shizuku isn't running.", color = MaterialTheme.colorScheme.secondary)
                    Text(
                        "It stops whenever the phone restarts, so start it again after a reboot. This app's " +
                            "permission is remembered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        context.packageManager.getLaunchIntentForPackage(ShoulderTriggers.SHIZUKU_PACKAGE)
                            ?.let(context::startActivity)
                    }) { Text("Open Shizuku") }
                }
                ShoulderTriggers.Status.NeedsPermission -> {
                    Text("Shizuku is running; allow this app to use it.", color = MaterialTheme.colorScheme.secondary)
                    OutlinedButton(onClick = {
                        ShoulderTriggers.requestPermission { granted ->
                            if (granted) status = ShoulderTriggers.Status.Ready
                        }
                    }) { Text("Grant access") }
                }
                ShoulderTriggers.Status.Ready -> {
                    Text(
                        "Ready. The triggers work while a video is open, and switch off when you leave it.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            testing = !testing
                            ShoulderTriggers.testMode = testing
                            if (testing) ShoulderTriggers.start(context) else ShoulderTriggers.stop()
                        }) { Text(if (testing) "Stop test" else "Test triggers") }
                        if (testing) {
                            Text(
                                when {
                                    lastPress != null -> "Pressed: $lastPress"
                                    running -> "Press a trigger…"
                                    else -> "Starting…"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
