package app.mizune.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.App
import app.mizune.BuildConfig

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
