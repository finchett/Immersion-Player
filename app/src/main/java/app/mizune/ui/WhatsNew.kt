package app.mizune.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.App
import app.mizune.BuildConfig
import app.mizune.update.AppUpdateState

/**
 * At the foot of the library from the moment an update is found: "1.2.0 is on its way", filling
 * up as it downloads, then "What's new in 1.2.0". Either opens its notes, the second with a way to
 * install it (on Android 12 and newer it also goes in by itself once the app is put away).
 */
@Composable
fun UpdatePill(app: App, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val update by app.updater.state.collectAsState()
    var readingNotes by remember { mutableStateOf(false) }
    val text = when (val state = update) {
        is AppUpdateState.Downloading -> "✦  ${state.version} is on its way · ${(state.progress * 100).toInt()}%"
        is AppUpdateState.Ready -> "✦  What's new in ${state.version}"
        is AppUpdateState.Installing -> "✦  Installing ${state.version}…"
        else -> return
    }
    val progress = (update as? AppUpdateState.Downloading)?.progress
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Surface(
        onClick = {
            // once it's downloaded its notes are kept for its first launch, which needn't show them again
            if (progress == null) app.updater.whatsNew.seen()
            readingNotes = true
        },
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .drawBehind { if (progress != null) drawRect(fill, size = Size(size.width * progress, size.height)) }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
    if (readingNotes) {
        when (val state = update) {
            is AppUpdateState.Downloading -> WhatsNewDialog(
                eyebrow = "On its way · ${(state.progress * 100).toInt()}%",
                version = state.version,
                notes = state.notes,
                confirm = "OK",
                onConfirm = { readingNotes = false },
                onDismiss = { readingNotes = false },
            )
            is AppUpdateState.Ready -> WhatsNewDialog(
                eyebrow = "Ready to install",
                version = state.version,
                notes = state.notes,
                confirm = "Update now",
                onConfirm = {
                    readingNotes = false
                    app.updater.install(context)
                },
                later = "Later",
                onDismiss = { readingNotes = false },
            )
            is AppUpdateState.Installing -> WhatsNewDialog(
                eyebrow = "Installing",
                version = state.version,
                notes = state.notes,
                confirm = "OK",
                onConfirm = { readingNotes = false },
                onDismiss = { readingNotes = false },
            )
            else -> {}
        }
    }
}

/** The first launch after an update: what it brought, once, unless it was read before it went in. */
@Composable
fun JustUpdated(app: App) {
    var notes by remember { mutableStateOf(app.updater.whatsNew.pending(BuildConfig.VERSION_NAME)) }
    val shown = notes ?: return
    fun close() {
        app.updater.whatsNew.seen()
        notes = null
    }
    WhatsNewDialog(
        eyebrow = "Just updated",
        version = BuildConfig.VERSION_NAME,
        notes = shown,
        confirm = "Let's go",
        onConfirm = ::close,
        onDismiss = ::close,
    )
}

@Composable
fun WhatsNewDialog(
    eyebrow: String,
    version: String,
    notes: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    later: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    eyebrow.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("What's new in $version")
            }
        },
        text = {
            if (notes.isNotBlank()) {
                ReleaseNotesText(notes, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            } else {
                Text("Fixes and polish.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = later?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}
